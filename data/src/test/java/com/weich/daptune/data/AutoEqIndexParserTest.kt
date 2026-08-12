package com.weich.daptune.data

import com.weich.daptune.core.model.AutoEqForm
import com.weich.daptune.core.model.AutoEqProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqIndexParserTest {
    @Test
    fun parsesRecommendedEntriesAndPreservesLiteralPlus() {
        val markdown = """
            # Recommended Results
            - [Apple AirPods Pro 2 (51dB + ANC)](./crinacle/711%20in-ear/Apple%20AirPods%20Pro%202%20(51dB%20+%20ANC))
            - [Sennheiser HD 600](./oratory1990/over-ear/Sennheiser%20HD%20600)
        """.trimIndent()

        val results = AutoEqIndexParser.parse(markdown)

        assertEquals(2, results.size)
        assertEquals("Apple AirPods Pro 2 (51dB + ANC)", results[0].name)
        assertEquals("crinacle", results[0].measurementSource)
        assertEquals(AutoEqForm.IN_EAR, results[0].form)
        assertEquals(AutoEqForm.OVER_EAR, results[1].form)
        assertEquals(
            "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/" +
                "crinacle/711%20in-ear/Apple%20AirPods%20Pro%202%20(51dB%20+%20ANC)/" +
                "Apple%20AirPods%20Pro%202%20%2851dB%20%2B%20ANC%29%20GraphicEQ.txt",
            AutoEqIndexParser.graphicEqUrl(results[0]),
        )
    }

    @Test
    fun rejectsTraversalMismatchesAndMalformedLines() {
        val markdown = """
            - [Safe name](./source/in-ear/Different%20name)
            - [Traversal](./source/../Traversal)
            - [Absolute](https://example.com/result)
            ordinary text
        """.trimIndent()

        assertTrue(AutoEqIndexParser.parse(markdown).isEmpty())
    }

    @Test
    fun searchRanksExactAndPrefixMatchesBeforeLooseTokenMatches() {
        val catalog = AutoEqCatalog(
            listOf(
                profile("Sony WH-1000XM5"),
                profile("Sony WH-1000XM5 (ANC on)"),
                profile("Acme Sony Reference WH-1000XM5"),
                profile("Sony WH-1000XM4"),
            ),
        )

        val results = catalog.search("Sony WH-1000XM5", limit = 10)

        assertEquals("Sony WH-1000XM5", results[0].name)
        assertEquals("Sony WH-1000XM5 (ANC on)", results[1].name)
        assertEquals("Acme Sony Reference WH-1000XM5", results[2].name)
    }

    @Test
    fun searchMatchesSeparatedModelTokens() {
        val catalog = AutoEqCatalog(
            listOf(
                profile("Philips SHP9600"),
                profile("Philips SHP9500"),
                profile("Unrelated Headphone"),
            ),
        )

        val results = catalog.search("philips 9600", limit = 10)

        assertEquals(listOf("Philips SHP9600"), results.map(AutoEqProfile::name))
    }

    private fun profile(name: String) = AutoEqProfile(
        name = name,
        relativePath = "./source/over-ear/${name.replace(' ', '%')}",
        measurementSource = "source",
        form = AutoEqForm.OVER_EAR,
    )
}
