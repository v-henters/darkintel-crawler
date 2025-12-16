package com.darkintel.crawler.lambda.dto

import kotlinx.serialization.Serializable

@Serializable
data class RunNowRequest(
    val sourceId: String
)
