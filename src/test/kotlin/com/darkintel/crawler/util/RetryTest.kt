package com.darkintel.crawler.util

import com.darkintel.crawler.error.FatalCrawlerException
import com.darkintel.crawler.error.RetriableCrawlerException
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetryTest {

    @Test
    fun `success on first attempt calls block once and returns result`() = runTest {
        var calls = 0
        val result = retry {
            calls++
            "ok"
        }

        assertEquals(1, calls, "Block should be invoked exactly once for immediate success")
        assertEquals("ok", result)
    }

    @Test
    fun `retriable exception then success within maxAttempts`() = runTest {
        val n = 4
        var calls = 0

        val result = retry(maxAttempts = n, initialDelayMs = 1, factor = 1.0) {
            calls++
            if (calls < n) throw RetriableCrawlerException("temporary failure #$calls")
            "done"
        }

        assertEquals(n, calls, "Block should be invoked N times where last attempt succeeds")
        assertEquals("done", result)
    }

    @Test
    fun `retriable exception exceeding maxAttempts throws and calls block maxAttempts times`() = runTest {
        var calls = 0

        val ex = assertFailsWith<RetriableCrawlerException> {
            retry(maxAttempts = 3, initialDelayMs = 1, factor = 1.0) {
                calls++
                throw RetriableCrawlerException("always failing")
            }
        }

        assertEquals(3, calls, "Block should be invoked exactly maxAttempts times before giving up")
        // Optional: verify message to ensure it is the retriable one
        assertEquals("always failing", ex.message)
    }

    @Test
    fun `fatal exception is rethrown immediately without retries`() = runTest {
        var calls = 0

        assertFailsWith<FatalCrawlerException> {
            retry(maxAttempts = 5) {
                calls++
                throw FatalCrawlerException("fatal error")
            }
        }

        assertEquals(1, calls, "Block should be invoked exactly once for fatal exception")
    }
}
