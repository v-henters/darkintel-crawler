package com.darkintel.crawler.runner

import com.darkintel.crawler.config.AppConfig
import com.darkintel.crawler.config.SourceConfig
import com.darkintel.crawler.dynamodb.DocumentRepository
import com.darkintel.crawler.dynamodb.SourceScheduleRepository
import com.darkintel.crawler.dynamodb.SourceStateRepository
import com.darkintel.crawler.client.IngestApiClient
import com.darkintel.crawler.model.SourceState
import com.darkintel.crawler.parser.ParserFactory
import com.darkintel.crawler.redis.DistributedLockManager
import com.darkintel.crawler.redis.RateLimiter
import com.darkintel.crawler.util.getLogger
import com.darkintel.crawler.util.retry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.ZoneOffset
import com.darkintel.crawler.sns.SnsPublisher

private object CrawlerRunnerLogger

class CrawlerRunner(
    private val config: AppConfig,
    private val sourceStateRepository: SourceStateRepository,
    private val documentRepository: DocumentRepository,
    private val ingestApiClient: IngestApiClient,
    private val sourceScheduleRepository: SourceScheduleRepository? = null,
    private val rateLimiter: RateLimiter? = null,
    private val lockManager: DistributedLockManager? = null,
    private val snsPublisher: SnsPublisher? = null
) {

    private val logger = getLogger(CrawlerRunnerLogger::class)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun runAll() = runOnce()

    suspend fun runSingle(sourceId: String) {
        val source = config.sources.find { it.id == sourceId }
        if (source == null) {
            logger.warn("Requested runSingle for unknown source: {}", sourceId)
            return
        }
        crawlSingleSource(source)
    }

    suspend fun runOnce() = coroutineScope {
        val concurrency = config.scheduler.concurrency.coerceAtLeast(1)
        val semaphore = Semaphore(concurrency)

        config.sources.forEach { source ->
            launch {
                semaphore.withPermit {
                    crawlSingleSource(source)
                }
            }
        }
    }

    private suspend fun crawlSingleSource(source: SourceConfig) {
        val sourceId = source.id

        // 락 매니저가 설정된 경우 락을 먼저 획득한다.
        if (lockManager != null) {
            val acquired = lockManager.tryLock(sourceId)
            if (!acquired) {
                logger.info("Skip source due to active lock: {}", sourceId)
                return
            }
            logger.info("Lock acquired for source {}", sourceId)
        }

        try {
            // 스케줄 저장소가 제공되는 경우 스케줄을 확인한다.
            if (sourceScheduleRepository != null) {
                val scheduleConfig = retry { sourceScheduleRepository.get(sourceId) }

                if (scheduleConfig != null && !scheduleConfig.enabled) {
                    logger.info("Skipping source {} because it is disabled by schedule config", sourceId)
                    return
                }

                if (scheduleConfig != null &&
                    scheduleConfig.allowedStartHourUtc != null &&
                    scheduleConfig.allowedEndHourUtc != null
                ) {
                    val currentHour = Instant.now().atOffset(ZoneOffset.UTC).hour
                    val start = scheduleConfig.allowedStartHourUtc
                    val end = scheduleConfig.allowedEndHourUtc

                    val withinWindow = if (start <= end) {
                        currentHour in start until end
                    } else {
                        (currentHour >= start) || (currentHour < end)
                    }

                    if (!withinWindow) {
                        logger.info(
                            "Skipping source {} because current UTC hour {} is outside allowed window [{}, {})",
                            sourceId, currentHour, start, end
                        )
                        return
                    }
                }
            }

            // 소스 단위 레이트 리밋 체크
            rateLimiter?.checkAndConsumeOrThrow(sourceId)
            logger.info("Starting crawl for source {} ({})", sourceId, source.name)

            // 이전 크롤 상태를 로드한다 (없을 수 있음)
            val state: SourceState? = retry { sourceStateRepository.get(sourceId) }

            // 파서를 선택하고 크롤링을 수행
            val parser = ParserFactory.create(source)
            val docs = parser.crawl(source, state)
            logger.info("Source {} produced {} candidate docs", sourceId, docs.size)

            var maxPostedAt: Instant? = null

            for (doc in docs) {
                val isNew = retry { documentRepository.insertIfNew(doc) }
                if (!isNew) {
                    // 중복 문서에 대한 과도한 로그를 피하기 위해 건너뛴다.
                    continue
                }

                // Publish to SNS asynchronously (do not interrupt flow on failure)
                snsPublisher?.let { publisher ->
                    backgroundScope.launch {
                        publisher.publishDoc(doc, source.name)
                    }
                }

                // 정규화된 문서를 인제스트 API로 전송
                retry { ingestApiClient.send(doc) }
                logger.info("Ingested new doc for source {}: {}", sourceId, doc.url)

                // rawMeta에 "posted_at" 값이 있으면 그 중 가장 최신 값을 추적하고, 없으면 collectedAt을 사용한다.
                val postedAt = (doc.rawMeta["posted_at"] as? String)?.let {
                    try {
                        Instant.parse(it)
                    } catch (_: Exception) {
                        null
                    }
                } ?: doc.collectedAt

                if (maxPostedAt == null || postedAt.isAfter(maxPostedAt)) {
                    maxPostedAt = postedAt
                }
            }

            // 크롤링이 정상적으로 끝난 경우 상태를 성공 상태로 갱신
            retry { sourceStateRepository.upsertSuccess(sourceId, maxPostedAt) }
            logger.info("Finished crawl for source {}. lastSeenPostedAt={}", sourceId, maxPostedAt)
        } catch (e: Exception) {
            // 크롤링 실패 시 상태를 에러 상태로 갱신
            try {
                retry { sourceStateRepository.upsertError(sourceId, e) }
            } catch (_: Exception) {
                // 최선만 시도하고, 상태 갱신 중 발생한 오류는 무시한다.
            }
            logger.error("Error while crawling source {}: {}", sourceId, e.message, e)
        } finally {
            // 획득했던 락을 해제
            lockManager?.release(sourceId)
            if (lockManager != null) {
                logger.info("Lock released for source {}", sourceId)
            }
        }
    }
}
