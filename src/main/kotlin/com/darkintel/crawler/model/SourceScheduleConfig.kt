package com.darkintel.crawler.model

data class SourceScheduleConfig(
    val sourceId: String,
    val enabled: Boolean,
    val allowedStartHourUtc: Int?,
    val allowedEndHourUtc: Int?
)
