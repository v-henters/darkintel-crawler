package com.darkintel.crawler.lambda.dto

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleUpdateRequest(
    val sourceId: String,
    val enabled: Boolean,
    val allowedStartHourUtc: Int? = null,
    val allowedEndHourUtc: Int? = null
)
