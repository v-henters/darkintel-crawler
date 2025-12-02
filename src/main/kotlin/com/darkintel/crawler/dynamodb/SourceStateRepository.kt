package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.SourceState
import java.time.Instant

interface SourceStateRepository {
    suspend fun get(sourceId: String): SourceState?

    /**
     * 크롤링이 정상적으로 완료되었을 때 상태를 갱신한다:
     * - lastCrawledAt = 현재 시각
     * - lastSuccessAt = 현재 시각
     * - lastSeenPostedAt = 전달받은 값(없을 수 있음)
     * - lastErrorAt, lastErrorMessage 는 초기화
     */
    suspend fun upsertSuccess(sourceId: String, lastSeenPostedAt: Instant?)

    /**
     * 크롤링이 실패했을 때 상태를 갱신한다:
     * - lastCrawledAt = 현재 시각
     * - lastErrorAt = 현재 시각
     * - lastErrorMessage = error.toString()
     * - lastSuccessAt 는 변경하지 않는다
     */
    suspend fun upsertError(sourceId: String, error: Throwable)
}
