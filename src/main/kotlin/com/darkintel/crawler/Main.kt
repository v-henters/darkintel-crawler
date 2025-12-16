package com.darkintel.crawler

import com.darkintel.crawler.client.HttpIngestApiClient
import com.darkintel.crawler.config.ConfigLoader
import com.darkintel.crawler.config.RuntimeConfigLoader
import com.darkintel.crawler.dynamodb.DynamoDbDocumentRepository
import com.darkintel.crawler.dynamodb.DynamoDbClientProvider
import com.darkintel.crawler.dynamodb.DynamoDbSourceStateRepository
import com.darkintel.crawler.dynamodb.DynamoDbDistributedLockManager
import com.darkintel.crawler.dynamodb.DynamoDbSourceScheduleRepository
import com.darkintel.crawler.redis.RedisDistributedLockManager
import com.darkintel.crawler.redis.RedisRateLimiter
import com.darkintel.crawler.redis.RedisClientProvider
import com.darkintel.crawler.redis.NoopRateLimiter
import com.darkintel.crawler.redis.DistributedLockManager
import com.darkintel.crawler.redis.RateLimiter
import com.darkintel.crawler.runner.CrawlerRunner
import com.darkintel.crawler.util.getLogger
import kotlinx.coroutines.runBlocking
import com.darkintel.crawler.sns.SnsClientProvider
import com.darkintel.crawler.sns.SnsPublisher

private object MainLogger

suspend fun runCrawlerOnce(args: Array<String>, sourceId: String? = null) {
    val runtimeConfig = RuntimeConfigLoader.load(args)
    val logger = getLogger(MainLogger::class)

    logger.info("darkweb-crawler starting...")
    logger.info("Config path: {}", runtimeConfig.configPath)
    logger.info("AWS region override: {}", runtimeConfig.awsRegion ?: "(default)")
    if (runtimeConfig.useRedisRateLimiter || runtimeConfig.useRedisLock) {
        logger.info("Redis URI: {}", runtimeConfig.redisUri ?: "redis://localhost:6379")
    } else {
        logger.info("Redis not used (both DARKWEB_USE_REDIS_RATELIMITER and DARKWEB_USE_REDIS_LOCK are false)")
    }

    val config = ConfigLoader.load(runtimeConfig.configPath)

    DynamoDbClientProvider.init(runtimeConfig.awsRegion)
    if (runtimeConfig.useRedisRateLimiter || runtimeConfig.useRedisLock) {
        RedisClientProvider.init(runtimeConfig.redisUri ?: "redis://localhost:6379")
    }

    logger.info("Backend base URL: {}", config.backendBaseUrl)
    logger.info("Total sources: {}", config.sources.size)
    logger.info("Concurrency level: {}", config.scheduler.concurrency)

    val sourceStateRepository = DynamoDbSourceStateRepository(
        tableName = config.dynamodb.sourceStateTable
    )
    val documentRepository = DynamoDbDocumentRepository(
        tableName = config.dynamodb.documentsTable
    )
    val scheduleRepository = DynamoDbSourceScheduleRepository(
        tableName = config.dynamodb.scheduleTable
    )

    val ingestApiClient = HttpIngestApiClient(
        backendBaseUrl = config.backendBaseUrl,
        backendApiToken = config.backendApiToken
    )

    val rateLimiter: RateLimiter? =
        if (runtimeConfig.useRedisRateLimiter) {
            RedisRateLimiter(maxRequestsPerMinute = 60)
        } else {
            NoopRateLimiter()
        }

    val lockManager: DistributedLockManager? =
        if (runtimeConfig.useRedisLock) {
            RedisDistributedLockManager(lockTtlSeconds = 60)
        } else {
            DynamoDbDistributedLockManager(
                tableName = config.dynamodb.locksTable,
                lockTtlSeconds = 300
            )
        }

    logger.info("RateLimiter implementation: {}", rateLimiter?.javaClass?.simpleName ?: "none")
    logger.info("LockManager implementation: {}", lockManager?.javaClass?.simpleName ?: "none")
    logger.info(
        "Using DynamoDB tables: source_state={}, documents={}, locks={}, schedule={}",
        config.dynamodb.sourceStateTable,
        config.dynamodb.documentsTable,
        config.dynamodb.locksTable,
        config.dynamodb.scheduleTable
    )

    // Initialize SNS publisher if topic ARN is provided
    val snsPublisher: SnsPublisher? = runtimeConfig.snsTopicArn?.takeIf { it.isNotBlank() }?.let { topicArn ->
        SnsClientProvider.init(runtimeConfig.awsRegion)
        SnsPublisher(topicArn) { SnsClientProvider.client }
    }

    val runner = CrawlerRunner(
        config = config,
        sourceStateRepository = sourceStateRepository,
        documentRepository = documentRepository,
        ingestApiClient = ingestApiClient,
        sourceScheduleRepository = scheduleRepository,
        rateLimiter = rateLimiter,
        lockManager = lockManager,
        snsPublisher = snsPublisher
    )

    if (sourceId != null) {
        logger.info("Run-now for single source: {}", sourceId)
        runner.runSingle(sourceId)
    } else {
        runner.runAll()
    }
    logger.info("Completed one run successfully.")
}

fun main(args: Array<String>) {
    // Initialize DynamoDB client with region from environment, if provided (also re-initialized later if overridden by runtime config)
    DynamoDbClientProvider.init(System.getenv("AWS_REGION"))

    runBlocking {
        runCrawlerOnce(args)
    }
}
