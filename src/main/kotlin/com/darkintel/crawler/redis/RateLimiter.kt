package com.darkintel.crawler.redis

interface RateLimiter {
    /**
     * 주어진 소스에 대해 레이트 리밋 토큰을 확인하고 하나 소모한다.
     * 제한을 초과한 경우 예외를 던져야 한다.
     */
    suspend fun checkAndConsumeOrThrow(sourceId: String)
}
