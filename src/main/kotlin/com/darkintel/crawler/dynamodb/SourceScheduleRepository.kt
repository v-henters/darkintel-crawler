package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.SourceScheduleConfig

interface SourceScheduleRepository {
    suspend fun get(sourceId: String): SourceScheduleConfig?
    suspend fun upsert(config: SourceScheduleConfig)
}
