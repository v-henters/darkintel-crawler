package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.redis.DistributedLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import java.time.Instant
import java.util.UUID

class DynamoDbDistributedLockManager(
    private val tableName: String,
    private val client: DynamoDbClient = DynamoDbClientProvider.client,
    private val lockTtlSeconds: Long = 300,
    private val instanceId: String = UUID.randomUUID().toString()
) : DistributedLockManager {

    private val logger = LoggerFactory.getLogger(DynamoDbDistributedLockManager::class.java)

    override suspend fun tryLock(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val expiresAtEpochSeconds = now.epochSecond + lockTtlSeconds

        val item = mapOf(
            "lock_id" to AttributeValue.builder().s(sourceId).build(),
            "owner_id" to AttributeValue.builder().s(instanceId).build(),
            "expires_at" to AttributeValue.builder().n(expiresAtEpochSeconds.toString()).build()
        )

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            // lock_id가 아직 존재하지 않을 때만 락 획득에 성공한다.
            .conditionExpression("attribute_not_exists(lock_id)")
            .build()

        return@withContext try {
            val envRegion = System.getenv("AWS_REGION") ?: System.getenv("AWS_DEFAULT_REGION")
            val clientRegion = envRegion ?: "unknown"
            logger.info("Using DynamoDB lock table: {}, region: {}", tableName, clientRegion)
            client.putItem(request)
            logger.info("DynamoDB lock acquired for source {} by {}", sourceId, instanceId)
            true
        } catch (e: ConditionalCheckFailedException) {
            // 이미 다른 인스턴스가 락을 보유하고 있다.
            logger.info("DynamoDB lock already held for source {}.", sourceId)
            false
        }
    }

    override suspend fun release(sourceId: String) = withContext(Dispatchers.IO) {
        val key = mapOf(
            "lock_id" to AttributeValue.builder().s(sourceId).build()
        )

        // owner_id가 현재 인스턴스의 instanceId와 일치할 때만 항목을 삭제한다.
        val request = DeleteItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .conditionExpression("owner_id = :owner")
            .expressionAttributeValues(
                mapOf(":owner" to AttributeValue.builder().s(instanceId).build())
            )
            .build()

        try {
            client.deleteItem(request)
            logger.info("DynamoDB lock released for source {} by {}", sourceId, instanceId)
        } catch (e: ConditionalCheckFailedException) {
            // 이 인스턴스가 소유하지 않았거나 이미 삭제된 락이므로 무시한다.
            logger.info("DynamoDB lock for source {} is not owned by this instance, ignoring.", sourceId)
        }
    }
}
