package com.weich.daptune.core.eq

import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.core.model.ProfileSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ImportedCurve(
    val suggestedName: String,
    val gainsDb: List<Double>,
    val source: ProfileSource,
    val warnings: List<String> = emptyList(),
) {
    val minimumDb: Double get() = gainsDb.min()
    val maximumDb: Double get() = gainsDb.max()
    val exceedsLimit: Boolean
        get() = minimumDb < -EqCurve.MAX_GAIN_DB || maximumDb > EqCurve.MAX_GAIN_DB
}

class CurveImportException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object CurveFileCodec {
    private const val FileFormat = "com.weich.daptune.profile"
    private const val FileVersion = 1
    private const val DefaultSampleRate = 48_000.0
    private const val MaxCharacters = 1_000_000

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun import(text: String, fileName: String): ImportedCurve {
        require(text.length <= MaxCharacters) { "文件过大" }
        val normalized = text.removePrefix("\uFEFF").trim()
        if (normalized.isEmpty()) throw CurveImportException("文件为空")
        return try {
            when {
                normalized.startsWith("{") -> importNative(normalized, fileName)
                graphicEqLines(normalized).isNotEmpty() -> importGraphicEq(normalized, fileName)
                normalized.lineSequence().any { activeFilterRegex.containsMatchIn(it) } ->
                    importParametricEq(normalized, fileName)
                else -> importDelimited(normalized, fileName)
            }
        } catch (error: CurveImportException) {
            throw error
        } catch (error: Exception) {
            throw CurveImportException("无法解析均衡器文件：${error.message ?: error::class.simpleName}", error)
        }
    }

    fun exportNative(name: String, curve: EqCurve): String = json.encodeToString(
        ProfileFileDto(
            name = name,
            bandPlan = DapBandPlan.id,
            frequenciesHz = DapBandPlan.frequenciesHz.asList(),
            gainsQ4 = curve.toQ4List(),
        ),
    )

    fun exportGraphicEq(curve: EqCurve): String = buildString {
        append("GraphicEQ: ")
        DapBandPlan.frequenciesHz.forEachIndexed { index, frequency ->
            if (index > 0) append("; ")
            append(frequency)
            append(' ')
            append(formatGain(curve.gainDb(index)))
        }
        append('\n')
    }

    private fun importNative(text: String, fileName: String): ImportedCurve {
        val dto = json.decodeFromString<ProfileFileDto>(text)
        if (dto.format != FileFormat || dto.version != FileVersion) {
            throw CurveImportException("不支持的 DapTune 文件版本")
        }
        if (dto.bandPlan != DapBandPlan.id || dto.frequenciesHz != DapBandPlan.frequenciesHz.asList()) {
            throw CurveImportException("文件使用了不兼容的频点")
        }
        val curve = try {
            EqCurve.ofQ4(dto.gainsQ4)
        } catch (error: IllegalArgumentException) {
            throw CurveImportException("文件中的增益超出 ±${EqCurve.MAX_GAIN_DB} dB", error)
        }
        return ImportedCurve(
            suggestedName = dto.name.ifBlank { baseName(fileName) },
            gainsDb = curve.toDbList(),
            source = ProfileSource.DAPTUNE_FILE,
        )
    }

    private fun importGraphicEq(text: String, fileName: String): ImportedCurve {
        val lines = graphicEqLines(text)
        if (lines.size != 1) throw CurveImportException("文件必须只包含一条 GraphicEQ 曲线")
        val payload = lines.single().substringAfter(':')
        val points = payload.split(';').mapIndexed { index, token ->
            val values = token.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            if (values.size != 2) throw CurveImportException("GraphicEQ 第 ${index + 1} 个频点格式错误")
            FrequencyGain(
                frequencyHz = values[0].toDoubleOrNull()
                    ?: throw CurveImportException("GraphicEQ 频率无效：${values[0]}"),
                gainDb = values[1].toDoubleOrNull()
                    ?: throw CurveImportException("GraphicEQ 增益无效：${values[1]}"),
            )
        }
        validatePoints(points)
        val preamp = parsePreamp(text)
        return ImportedCurve(
            suggestedName = baseName(fileName),
            gainsDb = sampleLogLinear(points).map { it + preamp },
            source = ProfileSource.GRAPHIC_EQ,
        )
    }

    private fun importParametricEq(text: String, fileName: String): ImportedCurve {
        val filters = mutableListOf<Biquad>()
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("Preamp", ignoreCase = true)) return@forEachIndexed
            if (!line.startsWith("Filter", ignoreCase = true)) return@forEachIndexed
            if (Regex("(?i):\\s*OFF\\b").containsMatchIn(line)) return@forEachIndexed
            val match = activeFilterRegex.find(line)
                ?: throw CurveImportException("第 ${index + 1} 行包含不支持的滤波器")
            val type = match.groupValues[1].uppercase()
            val frequency = match.groupValues[2].toDouble()
            val gain = match.groupValues[3].toDouble()
            val q = match.groupValues[4].takeIf(String::isNotBlank)?.toDouble() ?: DEFAULT_Q
            if (frequency <= 0.0 || frequency >= DefaultSampleRate / 2.0 || q <= 0.0) {
                throw CurveImportException("第 ${index + 1} 行的频率或 Q 值无效")
            }
            filters += Biquad.create(type, frequency, gain, q, DefaultSampleRate)
        }
        if (filters.isEmpty() && !preampRegex.containsMatchIn(text)) {
            throw CurveImportException("没有找到可用的参数均衡器滤波器")
        }
        val preamp = parsePreamp(text)
        val gains = DapBandPlan.frequenciesHz.map { frequency ->
            preamp + filters.sumOf { it.magnitudeDb(frequency.toDouble(), DefaultSampleRate) }
        }
        return ImportedCurve(
            suggestedName = baseName(fileName),
            gainsDb = gains,
            source = ProfileSource.PARAMETRIC_EQ,
            warnings = listOf("参数均衡器按 48 kHz 转换"),
        )
    }

    private fun importDelimited(text: String, fileName: String): ImportedCurve {
        val points = mutableListOf<FrequencyGain>()
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val values = line.split(Regex("[,\\t\\s]+"))
                .map(String::trim)
                .filter(String::isNotBlank)
            if (values.size < 2) {
                if (index == 0) return@forEachIndexed
                throw CurveImportException("第 ${index + 1} 行需要频率和增益")
            }
            val frequency = values[0].toDoubleOrNull()
            val gain = values[1].toDoubleOrNull()
            if (frequency == null || gain == null) {
                if (index == 0) return@forEachIndexed
                throw CurveImportException("第 ${index + 1} 行不是有效数字")
            }
            points += FrequencyGain(frequency, gain)
        }
        validatePoints(points)
        return ImportedCurve(
            suggestedName = baseName(fileName),
            gainsDb = sampleLogLinear(points),
            source = ProfileSource.CSV,
        )
    }

    private fun sampleLogLinear(points: List<FrequencyGain>): List<Double> {
        val sorted = points.sortedBy(FrequencyGain::frequencyHz)
        return DapBandPlan.frequenciesHz.map { targetInt ->
            val target = targetInt.toDouble()
            if (target < sorted.first().frequencyHz || target > sorted.last().frequencyHz) {
                0.0
            } else {
                val exact = sorted.indexOfFirst { it.frequencyHz == target }
                if (exact >= 0) {
                    sorted[exact].gainDb
                } else {
                    val upperIndex = sorted.indexOfFirst { it.frequencyHz > target }
                    val lower = sorted[upperIndex - 1]
                    val upper = sorted[upperIndex]
                    val ratio = (log10(target) - log10(lower.frequencyHz)) /
                        (log10(upper.frequencyHz) - log10(lower.frequencyHz))
                    lower.gainDb + (upper.gainDb - lower.gainDb) * ratio
                }
            }
        }
    }

    private fun validatePoints(points: List<FrequencyGain>) {
        if (points.size < 2) throw CurveImportException("至少需要两个频点")
        if (points.any { !it.frequencyHz.isFinite() || !it.gainDb.isFinite() || it.frequencyHz <= 0.0 }) {
            throw CurveImportException("文件包含无效频率或增益")
        }
        val sorted = points.sortedBy(FrequencyGain::frequencyHz)
        if (sorted.zipWithNext().any { (first, second) -> first.frequencyHz == second.frequencyHz }) {
            throw CurveImportException("文件包含重复频率")
        }
    }

    private fun parsePreamp(text: String): Double =
        preampRegex.find(text)?.groupValues?.get(1)?.toDouble() ?: 0.0

    private fun graphicEqLines(text: String): List<String> = text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.startsWith("GraphicEQ", ignoreCase = true) && ':' in it }
        .toList()

    private fun baseName(fileName: String): String = fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.', fileName)
        .removeSuffix("_GraphicEQ")
        .removeSuffix(" GraphicEQ")
        .removeSuffix("_ParametricEQ")
        .removeSuffix(" ParametricEQ")
        .trim()
        .ifBlank { "导入配置" }

    private fun formatGain(value: Double): String =
        java.lang.String.format(java.util.Locale.US, "%.4f", value)
            .trimEnd('0')
            .trimEnd('.')

    @Serializable
    private data class ProfileFileDto(
        val format: String = FileFormat,
        val version: Int = FileVersion,
        val name: String,
        @SerialName("band_plan") val bandPlan: String,
        @SerialName("frequencies_hz") val frequenciesHz: List<Int>,
        @SerialName("gains_q4") val gainsQ4: List<Int>,
    )

    private data class FrequencyGain(val frequencyHz: Double, val gainDb: Double)

    private data class Biquad(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a0: Double,
        val a1: Double,
        val a2: Double,
    ) {
        fun magnitudeDb(frequency: Double, sampleRate: Double): Double {
            val omega = 2.0 * PI * frequency / sampleRate
            val numeratorReal = b0 + b1 * cos(omega) + b2 * cos(2.0 * omega)
            val numeratorImaginary = -b1 * sin(omega) - b2 * sin(2.0 * omega)
            val denominatorReal = a0 + a1 * cos(omega) + a2 * cos(2.0 * omega)
            val denominatorImaginary = -a1 * sin(omega) - a2 * sin(2.0 * omega)
            val numeratorPower = numeratorReal * numeratorReal + numeratorImaginary * numeratorImaginary
            val denominatorPower = denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary
            return 10.0 * log10(numeratorPower / denominatorPower)
        }

        companion object {
            fun create(
                type: String,
                centerFrequency: Double,
                gainDb: Double,
                q: Double,
                sampleRate: Double,
            ): Biquad {
                val amplitude = 10.0.pow(gainDb / 40.0)
                val omega = 2.0 * PI * centerFrequency / sampleRate
                val cosine = cos(omega)
                val alpha = sin(omega) / (2.0 * q)
                return when (type) {
                    "PK", "PEQ" -> Biquad(
                        b0 = 1.0 + alpha * amplitude,
                        b1 = -2.0 * cosine,
                        b2 = 1.0 - alpha * amplitude,
                        a0 = 1.0 + alpha / amplitude,
                        a1 = -2.0 * cosine,
                        a2 = 1.0 - alpha / amplitude,
                    )
                    "LS" -> {
                        val root = 2.0 * sqrt(amplitude) * alpha
                        Biquad(
                            b0 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine + root),
                            b1 = 2.0 * amplitude * ((amplitude - 1.0) - (amplitude + 1.0) * cosine),
                            b2 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine - root),
                            a0 = (amplitude + 1.0) + (amplitude - 1.0) * cosine + root,
                            a1 = -2.0 * ((amplitude - 1.0) + (amplitude + 1.0) * cosine),
                            a2 = (amplitude + 1.0) + (amplitude - 1.0) * cosine - root,
                        )
                    }
                    "HS" -> {
                        val root = 2.0 * sqrt(amplitude) * alpha
                        Biquad(
                            b0 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine + root),
                            b1 = -2.0 * amplitude * ((amplitude - 1.0) + (amplitude + 1.0) * cosine),
                            b2 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine - root),
                            a0 = (amplitude + 1.0) - (amplitude - 1.0) * cosine + root,
                            a1 = 2.0 * ((amplitude - 1.0) - (amplitude + 1.0) * cosine),
                            a2 = (amplitude + 1.0) - (amplitude - 1.0) * cosine - root,
                        )
                    }
                    else -> throw CurveImportException("不支持的滤波器类型：$type")
                }
            }
        }
    }

    private const val DEFAULT_Q = 0.7071067811865476
    private val preampRegex = Regex("(?im)^\\s*Preamp\\s*:\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*dB\\s*$")
    private val activeFilterRegex = Regex(
        "(?i)^\\s*Filter(?:\\s+\\d+)?\\s*:\\s*ON\\s+(PK|PEQ|LS|HS)\\s+" +
            "Fc\\s+([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*Hz\\s+" +
            "Gain\\s+([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*dB" +
            "(?:\\s+Q\\s+([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)))?\\s*$",
    )
}
