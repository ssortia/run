package com.ssortia.runtimer

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Формат даты последней пробежки недели: «20 октября». Создаётся один раз на экран.
 * Шаблон явный, как у [runFormatters]. Не `d MMM`: сокращённый месяц в `ru` пишется с точкой,
 * и в JDK и на телефоне она может различаться. Года нет: программа длится 2–3 месяца.
 */
fun progressDateFormatter(zone: ZoneId, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", locale).withZone(zone)

fun progressDateText(startedAtMs: Long, formatter: DateTimeFormatter): String =
    formatter.format(Instant.ofEpochMilli(startedAtMs))
