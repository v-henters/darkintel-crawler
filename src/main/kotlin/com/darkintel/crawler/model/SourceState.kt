package com.darkintel.crawler.model

import java.time.Instant

data class SourceState(
    val sourceId: String,
    val lastCrawledAt: Instant?,
    val lastSuccessAt: Instant?,
    val lastErrorAt: Instant?,
    val lastErrorMessage: String?,
    val lastSeenPostedAt: Instant?
)
