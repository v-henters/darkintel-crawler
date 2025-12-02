package com.darkintel.crawler.model

data class IngestRawRequest(
    val sourceId: String,
    val title: String,
    val url: String,
    val contentText: String,
    val attachmentUrls: List<String>,
    val rawMeta: Map<String, Any?>,
    val collectedAt: String      // ISO8601 형식의 수집 시각 문자열
)
