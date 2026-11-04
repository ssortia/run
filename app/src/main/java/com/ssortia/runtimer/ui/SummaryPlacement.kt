package com.ssortia.runtimer.ui

/**
 * Раскладка формы: итог занимает слот свободного места над нижней частью (карточки,
 * предупреждение, ошибка). Отступ под «Старт» в нижнюю часть не входит: он уже вычтен из области.
 *
 * [useLarge] — время итога крупным стилем, [slotHeight] — высота слота итога
 * (итог стоит по его центру), [totalHeight] — высота всей раскладки: не меньше
 * области, а больше неё, когда итог не помещается и колонка прокручивается.
 */
data class SummaryPlacement(val useLarge: Boolean, val slotHeight: Int, val totalHeight: Int)

/**
 * Выбирает размер итога по высоте области [viewport], нижней части [bottom] и итога, всё в
 * пикселях. Крупный берётся, если карточки свёрнуты ([cardExpanded] false, как в макете: с
 * раскрытым барабаном время мельче), он помещается в свободное место по высоте и его время не шире
 * строки ([largeFitsWidth]), иначе малый, даже если не помещается и он. [smallSummary] меряет
 * малый вариант и вызывается, только когда крупный не подошёл: это единственное место решения,
 * раскладка не проверяет условие сама.
 */
fun summaryPlacement(
    viewport: Int,
    bottom: Int,
    largeSummary: Int,
    largeFitsWidth: Boolean,
    cardExpanded: Boolean,
    smallSummary: () -> Int,
): SummaryPlacement {
    val free = viewport - bottom
    val useLarge = !cardExpanded && largeFitsWidth && largeSummary <= free
    val summaryHeight = if (useLarge) largeSummary else smallSummary()
    val slot = maxOf(free, summaryHeight)
    return SummaryPlacement(useLarge = useLarge, slotHeight = slot, totalHeight = slot + bottom)
}
