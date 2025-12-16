package com.darkintel.crawler.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun toIsoString(instant: Instant): String {
        return instant.atOffset(ZoneOffset.UTC).format(formatter)
    }
}
