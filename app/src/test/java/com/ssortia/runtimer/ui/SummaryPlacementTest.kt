package com.ssortia.runtimer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPlacementTest {
    @Test
    fun largeFitsTakesAllFreeSpace() {
        assertEquals(
            SummaryPlacement(useLarge = true, slotHeight = 600, totalHeight = 1000),
            summaryPlacement(viewport = 1000, bottom = 400, largeSummary = 300, smallSummary = 250),
        )
    }

    @Test
    fun largeEqualToFreeSpaceStillFits() {
        assertEquals(
            SummaryPlacement(useLarge = true, slotHeight = 300, totalHeight = 1000),
            summaryPlacement(viewport = 1000, bottom = 700, largeSummary = 300, smallSummary = 250),
        )
    }

    @Test
    fun largeOnePixelTooBigFallsBackToSmall() {
        assertEquals(
            SummaryPlacement(useLarge = false, slotHeight = 299, totalHeight = 1000),
            summaryPlacement(viewport = 1000, bottom = 701, largeSummary = 300, smallSummary = 250),
        )
    }

    @Test
    fun expandedCardLeavesRoomOnlyForSmall() {
        assertEquals(
            SummaryPlacement(useLarge = false, slotHeight = 260, totalHeight = 1000),
            summaryPlacement(viewport = 1000, bottom = 740, largeSummary = 300, smallSummary = 250),
        )
    }

    @Test
    fun neitherFitsLayoutScrolls() {
        assertEquals(
            SummaryPlacement(useLarge = false, slotHeight = 250, totalHeight = 1150),
            summaryPlacement(viewport = 1000, bottom = 900, largeSummary = 300, smallSummary = 250),
        )
    }

    @Test
    fun bottomTallerThanViewport() {
        assertEquals(
            SummaryPlacement(useLarge = false, slotHeight = 250, totalHeight = 1450),
            summaryPlacement(viewport = 1000, bottom = 1200, largeSummary = 300, smallSummary = 250),
        )
    }
}
