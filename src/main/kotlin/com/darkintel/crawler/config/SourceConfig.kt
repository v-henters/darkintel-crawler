package com.darkintel.crawler.config

data class SourceConfig(
    val id: String,              // UUID 문자열
    val name: String,
    val baseUrl: String,
    val parserType: String?,     // 예: "BASIC" | "FORUM" | "MARKET" | null
    val crawlIntervalMinutes: Int
)
