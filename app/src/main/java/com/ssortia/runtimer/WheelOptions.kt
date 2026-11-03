package com.ssortia.runtimer

/** Шаг барабана секунд: секунды выбираются с шагом 5. */
const val SECONDS_WHEEL_STEP = 5

/**
 * Значения барабана: от 0 до [max] с шагом [step], последнее кратное шагу не больше [max]
 * (для секунд `MAX_SECONDS` и шаг 5 дают 0…55). Если [current] не кратно шагу (например,
 * 7 секунд из прежней версии), оно вставляется на своё место, чтобы открытие барабана молча
 * не меняло настройку. Значение вне `0..max` прижимается к краю и отдельно не добавляется.
 */
fun wheelOptions(max: Int, step: Int, current: Int): List<Int> {
    val options = (0..max step step).toMutableList()
    val value = current.coerceIn(0, max)
    val index = options.binarySearch(value)
    if (index < 0) options.add(-index - 1, value)
    return options
}

/**
 * Индекс выбранного значения в [options]. Значение, которого в списке нет, даёт ближайшее
 * не меньшее, а значение больше последнего даёт последний индекс.
 */
fun wheelIndexOf(options: List<Int>, value: Int): Int {
    val index = options.binarySearch(value)
    return if (index >= 0) index else (-index - 1).coerceAtMost(options.lastIndex)
}

/**
 * Значение, на котором остановился барабан, или null, если настройка не меняется. Сравнивается
 * с сохранённым [value], а не с показанной строкой: неверное сохранённое значение (120 минут
 * показаны как 99) остановка на этой строке исправляет.
 */
fun wheelPick(options: List<Int>, centerIndex: Int, value: Int): Int? =
    options[centerIndex].takeIf { it != value }
