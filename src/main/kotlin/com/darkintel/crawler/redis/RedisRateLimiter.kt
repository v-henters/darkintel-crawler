package com.darkintel.crawler.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.darkintel.crawler.util.getLogger

class RateLimitExceededException(message: String) : RuntimeException(message)

class RedisRateLimiter(
    private val maxRequestsPerMinute: Int = 60
) : RateLimiter {

    private val syncCommands = RedisClientProvider.syncCommands
    private val logger = getLogger(RedisRateLimiter::class)

    override suspend fun checkAndConsumeOrThrow(sourceId: String) = withContext(Dispatchers.IO) {
        val key = "rate:$sourceId"

        val current: Long = syncCommands.incr(key)

        if (current == 1L) {
            // 이 윈도우에서 첫 번째 호출이면 TTL을 설정한다.
            syncCommands.expire(key, 60)
        }

        if (current > maxRequestsPerMinute) {
            logger.warn("Rate limit exceeded for sourceId={} (count={})", sourceId, current)
            throw RateLimitExceededException("Rate limit exceeded for sourceId=$sourceId (count=$current)")
        }
    }
}
