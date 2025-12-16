package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.SourceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import java.time.Instant

class DynamoDbSourceStateRepository(
    private val tableName: String
) : SourceStateRepository {

    private val client = DynamoDbClientProvider.client

    override suspend fun get(sourceId: String): SourceState? = withContext(Dispatchers.IO) {
        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(
                mapOf(
                    "source_id" to AttributeValue.builder().s(sourceId).build()
                )
            )
            .build()

        val response = client.getItem(request)
        if (!response.hasItem()) {
            return@withContext null
        }

        val item = response.item()
        return@withContext SourceState(
            sourceId = sourceId,
            lastCrawledAt = item["last_crawled_at"]?.s()?.let { Instant.parse(it) },
            lastSuccessAt = item["last_success_at"]?.s()?.let { Instant.parse(it) },
            lastErrorAt = item["last_error_at"]?.s()?.let { Instant.parse(it) },
            lastErrorMessage = item["last_error_message"]?.s(),
            lastSeenPostedAt = item["last_seen_posted_at"]?.s()?.let { Instant.parse(it) }
        )
    }

    override suspend fun upsertSuccess(sourceId: String, lastSeenPostedAt: Instant?) = withContext(Dispatchers.IO) {
        val now = Instant.now()

        val item = mutableMapOf<String, AttributeValue>(
            "source_id" to AttributeValue.builder().s(sourceId).build(),
            "last_crawled_at" to AttributeValue.builder().s(now.toString()).build(),
            "last_success_at" to AttributeValue.builder().s(now.toString()).build()
        )

        if (lastSeenPostedAt != null) {
            item["last_seen_posted_at"] = AttributeValue.builder().s(lastSeenPostedAt.toString()).build()
        }

        // 오류 관련 필드를 제거한 상태로 항목을 덮어써서 에러 정보를 초기화한다.
        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()

        client.putItem(request)
        Unit
    }

    override suspend fun upsertError(sourceId: String, error: Throwable) = withContext(Dispatchers.IO) {
        val now = Instant.now()

        val item = mutableMapOf<String, AttributeValue>(
            "source_id" to AttributeValue.builder().s(sourceId).build(),
            "last_crawled_at" to AttributeValue.builder().s(now.toString()).build(),
            "last_error_at" to AttributeValue.builder().s(now.toString()).build(),
            "last_error_message" to AttributeValue.builder().s(error.toString()).build()
        )

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()

        client.putItem(request)
        Unit
    }
}
