package com.darkintel.crawler.dynamodb

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

object DynamoDbClientProvider {

    // 테이블 이름 (환경에 맞게 필요 시 변경)
    const val SOURCE_STATE_TABLE = "darkintel_crawler_source_state"
    const val DOCUMENTS_TABLE = "darkintel_crawler_documents"
    const val LOCKS_TABLE = "darkintel_crawler_locks"

    @Volatile
    private var _client: DynamoDbClient? = null

    val client: DynamoDbClient
        get() = _client ?: error("DynamoDbClientProvider not initialized. Call init() first.")

    fun init(regionOverride: String? = null) {
        if (_client != null) return

        val builder = DynamoDbClient.builder()
        if (!regionOverride.isNullOrBlank()) {
            builder.region(Region.of(regionOverride))
        }
        _client = builder.build()
    }
}
