package com.weich.daptune.core.eq

import com.weich.daptune.core.model.EqCurve

data class BuiltInPreset(
    val id: String,
    val name: String,
    val curve: EqCurve,
    val sortOrder: Int,
)

object BuiltInPresets {
    val all: List<BuiltInPreset> = listOf(
        preset("builtin.flat", "平直", 0, doubleArrayOf(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        )),
        preset("builtin.warm", "暖厚", 10, doubleArrayOf(
            4.0, 3.8, 3.4, 2.8, 2.1, 1.4, 0.8, 0.3, 0.0, -0.2,
            -0.4, -0.5, -0.4, -0.2, 0.0, 0.1, 0.0, -0.2, -0.4, -0.6,
        )),
        preset("builtin.powerful", "澎湃", 20, doubleArrayOf(
            6.0, 5.8, 5.0, 4.0, 2.8, 1.5, 0.5, -0.3, -0.8, -1.0,
            -0.8, -0.2, 0.5, 1.2, 1.6, 1.8, 1.6, 1.1, 0.3, -0.8,
        )),
        preset("builtin.loudness", "响度", 30, doubleArrayOf(
            4.5, 4.0, 3.2, 2.2, 1.1, 0.0, -0.7, -1.1, -1.3, -1.3,
            -1.0, -0.4, 0.3, 1.0, 1.7, 2.3, 2.7, 2.8, 2.5, 1.8,
        )),
        preset("builtin.vocal", "人声", 40, doubleArrayOf(
            -2.0, -1.8, -1.5, -1.0, -0.4, 0.2, 0.8, 1.4, 1.9, 2.3,
            2.7, 2.8, 2.5, 2.0, 1.2, 0.4, -0.2, -0.5, -0.7, -0.8,
        )),
        preset("builtin.bright", "明亮", 50, doubleArrayOf(
            -1.2, -1.0, -0.8, -0.5, -0.3, 0.0, 0.3, 0.6, 0.9, 1.2,
            1.6, 2.0, 2.4, 2.7, 3.0, 3.1, 3.0, 2.7, 2.2, 1.5,
        )),
        preset("builtin.soft", "柔和", 60, doubleArrayOf(
            1.8, 1.6, 1.3, 1.0, 0.7, 0.4, 0.2, 0.0, -0.1, -0.2,
            -0.3, -0.5, -0.8, -1.1, -1.5, -1.9, -2.2, -2.5, -2.8, -3.0,
        )),
    )

    private fun preset(id: String, name: String, sortOrder: Int, gains: DoubleArray): BuiltInPreset =
        BuiltInPreset(
            id = id,
            name = name,
            curve = EqTransforms.quantize(gains.asList(), OverflowMode.FIT).curve,
            sortOrder = sortOrder,
        )
}
