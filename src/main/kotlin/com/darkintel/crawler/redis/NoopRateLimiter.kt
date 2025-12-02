package com.darkintel.crawler.redis

class NoopRateLimiter : RateLimiter {
    override suspend fun checkAndConsumeOrThrow(sourceId: String) {
        // 아무 동작도 하지 않으며 항상 허용한다.
    }
}
