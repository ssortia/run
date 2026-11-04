package com.ssortia.runtimer.ui

/**
 * Раскладка формы: итог занимает слот свободного места над нижней частью
 * (карточки, предупреждение, ошибка, отступ под «Старт»).
 *
 * [useLarge] — время итога крупным стилем, [slotHeight] — высота слота итога
 * (итог стоит по его центру), [totalHeight] — высота всей раскладки: не меньше
 * области, а больше неё, когда итог не помещается и колонка прокручивается.
 */
data class SummaryPlacement(val useLarge: Boolean, val slotHeight: Int, val totalHeight: Int)

/**
 * Выбирает размер итога по высоте области [viewport], нижней части [bottom]
 * и двум вариантам итога, всё в пикселях. Крупный берётся, если помещается
 * в свободное место, иначе малый, даже если не помещается и он.
 */
fun summaryPlacement(viewport: Int, bottom: Int, largeSummary: Int, smallSummary: Int): SummaryPlacement {
    val free = viewport - bottom
    val useLarge = largeSummary <= free
    val summaryHeight = if (useLarge) largeSummary else smallSummary
    val slot = maxOf(free, summaryHeight)
    return SummaryPlacement(useLarge = useLarge, slotHeight = slot, totalHeight = slot + bottom)
}
