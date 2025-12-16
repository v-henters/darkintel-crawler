package com.darkintel.crawler.config

data class SchedulerConfig(
    val concurrency: Int,
    val requestTimeoutSeconds: Int
)
