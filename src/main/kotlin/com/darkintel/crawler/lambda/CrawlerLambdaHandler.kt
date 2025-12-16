package com.darkintel.crawler.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.darkintel.crawler.runCrawlerOnce
import com.darkintel.crawler.dynamodb.DynamoDbClientProvider
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * 단일 크롤러 실행을 수행하는 AWS Lambda 핸들러.
 *
 * EventBridge / Scheduler에서 다양한 JSON 페이로드로
 * 호출할 수 있도록 입력 타입을 Map<String, Any> 형태의
 * 일반적인 구조로 유지한다.
 */
class CrawlerLambdaHandler : RequestHandler<Map<String, Any>, String> {

    private val logger = LoggerFactory.getLogger(CrawlerLambdaHandler::class.java)

    override fun handleRequest(input: Map<String, Any>, context: Context): String {
        logger.info("CrawlerLambdaHandler invoked. RequestId={}", context.awsRequestId)
        logger.info("Starting darkweb-crawler run (environment: Lambda)")

        // Initialize DynamoDB client for Lambda
        DynamoDbClientProvider.init(System.getenv("AWS_REGION"))

        // Lambda 환경에서는 일반적으로 CLI 인자 대신 환경 변수를 사용해 설정을 전달한다.
        val args: Array<String> = emptyArray()
        val sourceId: String? = input["sourceId"] as? String

        return try {
            runBlocking {
                runCrawlerOnce(args, sourceId)
            }
            "OK"
        } catch (e: Exception) {
            logger.error("Error during Lambda crawl run: {}", e.message, e)
            // Lambda에서 예외를 던지면 해당 호출이 실패한 것으로 처리된다.
            throw e
        }
    }
}
