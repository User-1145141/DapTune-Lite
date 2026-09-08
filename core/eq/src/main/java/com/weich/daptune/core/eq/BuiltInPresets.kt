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
        preset("builtin.flat", "空预设", 0, doubleArrayOf(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        )),
        preset("builtin.vocal", "人声", 10, doubleArrayOf(
            -3.25, -1.9375, -1.1875, -0.6875, -0.125, 0.375, 0.8125, 1.3125, 2.0, 2.875,
            3.8125, 3.6875, 3.0, 2.1875, 1.625, 1.1875, 0.875, 0.6875, 0.5625, 0.375,
        )),
        preset("builtin.dynamic", "动感", 20, doubleArrayOf(
            5.0625, 3.25, 2.3125, 1.6875, 1.125, 0.6875, 0.4375, 0.3125, 0.25, 0.1875,
            0.4375, 1.0, 1.75, 2.625, 3.3125, 3.6875, 3.875, 4.0, 4.0, 4.0,
        )),
        preset("builtin.mellow", "柔和", 30, doubleArrayOf(
            -3.375, -2.125, -1.625, -1.1875, -0.75, -0.5, -0.375, -0.3125, -0.1875, -0.1875,
            -0.3125, -0.4375, -0.625, -0.9375, -1.3125, -1.75, -2.3125, -2.875, -3.375, -3.875,
        )),
        preset("builtin.warm", "温暖", 40, doubleArrayOf(
            5.8125, 4.5, 3.1875, 2.1875, 1.3125, 0.75, 0.5, 0.3125, 0.1875, 0.0,
            -0.3125, -0.875, -1.6875, -2.625, -3.3125, -3.6875, -3.875, -4.0, -4.0, -4.0,
        )),
        preset("builtin.bright", "明亮", 50, doubleArrayOf(
            -1.125, -2.375, -3.3125, -3.625, -3.5625, -3.0, -2.4375, -1.875, -1.25, -0.375,
            0.75, 2.125, 2.875, 2.8125, 2.3125, 1.625, 1.0625, 0.625, 0.375, 0.1875,
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
