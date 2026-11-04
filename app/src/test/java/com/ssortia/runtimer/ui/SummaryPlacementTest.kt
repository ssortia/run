package com.ssortia.runtimer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPlacementTest {
    private fun place(
        viewport: Int,
        bottom: Int,
        large: Int = 300,
        small: Int = 250,
        fitsWidth: Boolean = true,
        expanded: Boolean = false,
    ) = summaryPlacement(viewport, bottom, large, fitsWidth, expanded) { small }

    @Test
    fun `крупный влез и занимает всё свободное место`() {
        assertEquals(SummaryPlacement(useLarge = true, slotHeight = 600, totalHeight = 1000), place(1000, 400))
    }

    @Test
    fun `крупный ровно по свободному месту влезает`() {
        assertEquals(SummaryPlacement(useLarge = true, slotHeight = 300, totalHeight = 1000), place(1000, 700))
    }

    @Test
    fun `крупный на пиксель выше свободного места даёт малый`() {
        assertEquals(SummaryPlacement(useLarge = false, slotHeight = 299, totalHeight = 1000), place(1000, 701))
    }

    @Test
    fun `раскрытая карточка оставляет место только малому`() {
        assertEquals(SummaryPlacement(useLarge = false, slotHeight = 260, totalHeight = 1000), place(1000, 740))
    }

    @Test
    fun `не влез и малый, раскладка прокручивается`() {
        assertEquals(SummaryPlacement(useLarge = false, slotHeight = 250, totalHeight = 1150), place(1000, 900))
    }

    @Test
    fun `нижняя часть выше области`() {
        assertEquals(SummaryPlacement(useLarge = false, slotHeight = 250, totalHeight = 1450), place(1000, 1200))
    }

    @Test
    fun `крупный шире строки даёт малый, даже если влез по высоте`() {
        assertEquals(
            SummaryPlacement(useLarge = false, slotHeight = 600, totalHeight = 1000),
            place(1000, 400, fitsWidth = false),
        )
    }

    @Test
    fun `раскрытая карточка даёт малый, даже если крупный влез`() {
        assertEquals(
            SummaryPlacement(useLarge = false, slotHeight = 600, totalHeight = 1000),
            place(1000, 400, expanded = true),
        )
    }

    @Test
    fun `малый не меряется, когда крупный подошёл`() {
        var measured = false
        summaryPlacement(viewport = 1000, bottom = 400, largeSummary = 300, largeFitsWidth = true, cardExpanded = false) {
            measured = true
            250
        }
        assertEquals(false, measured)
    }
}
