package com.weich.daptune.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNamesTest {
    @Test
    fun unusedNameIsKeptUnchanged() {
        assertEquals(
            "监听曲线",
            ProfileNames.uniqueCopyName("监听曲线", listOf("平直")),
        )
    }

    @Test
    fun duplicateNameGetsCopySuffix() {
        assertEquals(
            "平直 副本",
            ProfileNames.uniqueCopyName("平直", listOf("平直")),
        )
    }

    @Test
    fun repeatedCopiesGetStableSequence() {
        assertEquals(
            "平直 副本 (3)",
            ProfileNames.uniqueCopyName(
                "平直 副本 (2)",
                listOf("平直", "平直 副本", "平直 副本 (2)"),
            ),
        )
    }

    @Test
    fun suffixRespectsMaximumLength() {
        val result = ProfileNames.uniqueCopyName("四".repeat(40), listOf("四".repeat(40)))

        assertEquals(40, result.length)
        assertEquals(true, result.endsWith(" 副本"))
    }
}
