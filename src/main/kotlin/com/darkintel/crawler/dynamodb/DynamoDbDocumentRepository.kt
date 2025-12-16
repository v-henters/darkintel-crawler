package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.NormalizedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant

class  DynamoDbDocumentRepository(
    private val tableName: String
) : DocumentRepository {

    private val client = DynamoDbClientProvider.client

    override suspend fun insertIfNew(doc: NormalizedDocument): Boolean = withContext(Dispatchers.IO) {
        val now = Instant.now()

        val key = mapOf(
            "source_id" to AttributeValue.builder().s(doc.sourceId).build(),
            "url" to AttributeValue.builder().s(doc.url).build()
        )

        val item = key.toMutableMap().apply {
            put("first_seen_at", AttributeValue.builder().s(now.toString()).build())
            put("last_seen_at", AttributeValue.builder().s(now.toString()).build())
            put("title", AttributeValue.builder().s(doc.title).build())
        }

        val putRequest = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .conditionExpression("attribute_not_exists(source_id) AND attribute_not_exists(url)")
            .build()

        try {
            client.putItem(putRequest)
            // 삽입 성공 → 새로운 문서
            return@withContext true
        } catch (e: ConditionalCheckFailedException) {
            // 이미 존재하는 문서 → last_seen_at만 갱신
            val updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET last_seen_at = :now")
                .expressionAttributeValues(
                    mapOf(
                        ":now" to AttributeValue.builder().s(now.toString()).build()
                    )
                )
                .build()

            client.updateItem(updateRequest)
            return@withContext false
        }
    }
}
