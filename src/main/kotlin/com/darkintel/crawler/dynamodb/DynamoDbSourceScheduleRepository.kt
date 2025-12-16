package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.SourceScheduleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

class DynamoDbSourceScheduleRepository(
    private val tableName: String
) : SourceScheduleRepository {

    private val client = DynamoDbClientProvider.client

    override suspend fun get(sourceId: String): SourceScheduleConfig? = withContext(Dispatchers.IO) {
        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(
                mapOf(
                    "source_id" to AttributeValue.builder().s(sourceId).build()
                )
            )
            .build()

        val response = client.getItem(request)
        if (!response.hasItem()) return@withContext null

        val item = response.item()
        val enabled = item["enabled"]?.bool() ?: true
        val start = item["allowed_start_hour_utc"]?.n()?.toIntOrNull()
        val end = item["allowed_end_hour_utc"]?.n()?.toIntOrNull()

        return@withContext SourceScheduleConfig(
            sourceId = sourceId,
            enabled = enabled,
            allowedStartHourUtc = start,
            allowedEndHourUtc = end
        )
    }

    override suspend fun upsert(config: SourceScheduleConfig) = withContext(Dispatchers.IO) {
        val item = mutableMapOf<String, AttributeValue>(
            "source_id" to AttributeValue.builder().s(config.sourceId).build(),
            "enabled" to AttributeValue.builder().bool(config.enabled).build()
        )

        if (config.allowedStartHourUtc != null) {
            item["allowed_start_hour_utc"] = AttributeValue.builder().n(config.allowedStartHourUtc.toString()).build()
        }
        if (config.allowedEndHourUtc != null) {
            item["allowed_end_hour_utc"] = AttributeValue.builder().n(config.allowedEndHourUtc.toString()).build()
        }

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()

        client.putItem(request)
        Unit
    }
}
