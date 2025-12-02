package com.darkintel.crawler.model

import java.time.Instant

data class NormalizedDocument(
    val sourceId: String,
    val title: String,
    val url: String,
    val contentText: String,
    val attachmentUrls: List<String>,
    val rawMeta: Map<String, Any?>,
    val collectedAt: Instant
)
