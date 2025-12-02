package com.darkintel.crawler

import com.darkintel.crawler.client.HttpIngestApiClient
import com.darkintel.crawler.config.ConfigLoader
import com.darkintel.crawler.config.RuntimeConfigLoader
import com.darkintel.crawler.dynamodb.DynamoDbDocumentRepository
import com.darkintel.crawler.dynamodb.DynamoDbClientProvider
import com.darkintel.crawler.dynamodb.DynamoDbSourceStateRepository
import com.darkintel.crawler.dynamodb.DynamoDbDistributedLockManager
import com.darkintel.crawler.redis.RedisDistributedLockManager
import com.darkintel.crawler.redis.RedisRateLimiter
import com.darkintel.crawler.redis.RedisClientProvider
import com.darkintel.crawler.redis.NoopRateLimiter
import com.darkintel.crawler.redis.DistributedLockManager
import com.darkintel.crawler.redis.RateLimiter
import com.darkintel.crawler.runner.CrawlerRunner
import com.darkintel.crawler.util.getLogger
import kotlinx.coroutines.runBlocking

private object MainLogger

suspend fun runCrawlerOnce(args: Array<String>) {
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

    val sourceStateRepository = DynamoDbSourceStateRepository()
    val documentRepository = DynamoDbDocumentRepository()

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
            DynamoDbDistributedLockManager(lockTtlSeconds = 300)
        }

    logger.info("RateLimiter implementation: {}", rateLimiter?.javaClass?.simpleName ?: "none")
    logger.info("LockManager implementation: {}", lockManager?.javaClass?.simpleName ?: "none")

    val runner = CrawlerRunner(
        config = config,
        sourceStateRepository = sourceStateRepository,
        documentRepository = documentRepository,
        ingestApiClient = ingestApiClient,
        rateLimiter = rateLimiter,
        lockManager = lockManager
    )

    runner.runOnce()
    logger.info("Completed one run successfully.")
}

fun main(args: Array<String>) = runBlocking {
    runCrawlerOnce(args)
}
