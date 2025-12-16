package com.darkintel.crawler.config

data class RuntimeConfig(
    val configPath: String,
    val awsRegion: String?,
    val redisUri: String?,
    val logLevel: String?,
    val useRedisRateLimiter: Boolean,
    val useRedisLock: Boolean,
    val snsTopicArn: String?
)
