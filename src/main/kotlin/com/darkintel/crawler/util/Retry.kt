package com.darkintel.crawler.util

import com.darkintel.crawler.error.FatalCrawlerException
import com.darkintel.crawler.error.RetriableCrawlerException
import kotlinx.coroutines.delay

private object RetryLoggerHolder

private val retryLogger = getLogger(RetryLoggerHolder::class)

suspend fun <T> retry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 300,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    var attempt = 1
    var delayMs = initialDelayMs

    while (true) {
        try {
            return block()
        } catch (e: FatalCrawlerException) {
            // 치명적인 오류는 재시도하지 않는다.
            throw e
        } catch (e: RetriableCrawlerException) {
            if (attempt >= maxAttempts) {
                retryLogger.error("Retry attempts exhausted after {} attempts: {}", attempt, e.message)
                throw e
            } else {
                retryLogger.warn("Retriable error on attempt {}/{}: {}. Retrying in {} ms...", attempt, maxAttempts, e.message, delayMs)
                delay(delayMs)
                attempt++
                delayMs = (delayMs * factor).toLong().coerceAtLeast(0L)
            }
        } catch (e: Exception) {
            // 알려지지 않은 예외는 가이드라인에 따라 재시도 가능한 오류로 취급한다.
            val wrapped = RetriableCrawlerException("Unexpected error: ${e.message}", e)
            if (attempt >= maxAttempts) {
                retryLogger.error("Retry attempts exhausted after {} attempts: {}", attempt, wrapped.message)
                throw wrapped
            } else {
                retryLogger.warn("Unexpected error on attempt {}/{}: {}. Retrying in {} ms...", attempt, maxAttempts, wrapped.message, delayMs)
                delay(delayMs)
                attempt++
                delayMs = (delayMs * factor).toLong().coerceAtLeast(0L)
            }
        }
    }
}
