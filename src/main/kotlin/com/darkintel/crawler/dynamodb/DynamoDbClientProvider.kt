package com.darkintel.crawler.dynamodb

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

object DynamoDbClientProvider {
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
