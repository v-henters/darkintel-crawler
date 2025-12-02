package com.darkintel.crawler.parser

import com.darkintel.crawler.config.SourceConfig
import com.darkintel.crawler.model.NormalizedDocument
import com.darkintel.crawler.model.SourceState
import com.darkintel.crawler.util.HttpClientProvider
import com.darkintel.crawler.util.getLogger
import com.darkintel.crawler.util.retry
import com.darkintel.crawler.error.RetriableCrawlerException
import com.darkintel.crawler.error.FatalCrawlerException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.get
import io.ktor.client.plugins.*
import io.ktor.http.isSuccess
import org.jsoup.Jsoup
import java.net.URI
import java.time.Instant

class BasicParser : Parser {

    private val logger = getLogger(BasicParser::class)

    override suspend fun crawl(source: SourceConfig, state: SourceState?): List<NormalizedDocument> {
        val client = HttpClientProvider.client

        val html: String = retry {
            try {
                val response = client.get(source.baseUrl)
                if (!response.status.isSuccess()) {
                    throw RetriableCrawlerException("Non-success HTTP status ${'$'}{response.status.value} for sourceId=${'$'}{source.id}")
                }
                response.body<String>()
            } catch (e: ClientRequestException) { // 4xx 응답 처리
                // 명백한 설정 오류가 아닌 이상 재시도 가능한 오류로 취급한다 (예: 잘못된 URL 형식으로 인한 400 등)
                val code = e.response.status.value
                if (code == 400) {
                    throw FatalCrawlerException("Bad request fetching source ${'$'}{source.id}. Check configuration/URL.", e)
                }
                throw RetriableCrawlerException("HTTP ${'$'}code while fetching source ${'$'}{source.id}", e)
            } catch (e: ServerResponseException) { // 5xx 응답 처리
                throw RetriableCrawlerException("5xx while fetching source ${'$'}{source.id}", e)
            } catch (e: HttpRequestTimeoutException) {
                throw RetriableCrawlerException("HTTP request timeout while fetching source ${'$'}{source.id}", e)
            } catch (e: ConnectTimeoutException) {
                throw RetriableCrawlerException("HTTP connect timeout while fetching source ${'$'}{source.id}", e)
            } catch (e: IllegalArgumentException) {
                // 잘못된 URL 또는 설정 오류일 가능성이 높음
                throw FatalCrawlerException("Invalid configuration or URL for source ${'$'}{source.id}", e)
            }
        }

        val document = Jsoup.parse(html, source.baseUrl)

        // 제목 추출
        val title = document.title().ifBlank { source.name }

        // 본문 텍스트 추출 (현재는 매우 단순한 방식)
        val contentText = document.body()?.text().orEmpty()

        // 첨부 파일로 보이는 URL들 추출
        val attachmentUrls = document.select("a[href]")
            .mapNotNull { element ->
                val href = element.attr("href")
                if (href.isNullOrBlank()) return@mapNotNull null

                // 상대 경로 URL을 절대 경로로 변환
                val resolved = resolveUrl(source.baseUrl, href)

                if (looksLikeAttachment(resolved)) resolved else null
            }
            .distinct()

        val collectedAt = Instant.now()

        val rawMeta: Map<String, Any?> = mapOf(
            "base_url" to source.baseUrl,
            "fetched_at" to collectedAt.toString(),
            "parser_type" to "BASIC"
        )

        val normalized = NormalizedDocument(
            sourceId = source.id,
            title = title,
            url = source.baseUrl,
            contentText = contentText,
            attachmentUrls = attachmentUrls,
            rawMeta = rawMeta,
            collectedAt = collectedAt
        )

        logger.info("Parsed {} document(s) for source {}", 1, source.id)
        return listOf(normalized)
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        return try {
            val base = URI(baseUrl)
            base.resolve(href).toString()
        } catch (e: Exception) {
            href
        }
    }

    private fun looksLikeAttachment(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(".txt", ".zip", ".gz", ".csv", ".json", ".pdf", ".7z", ".xz").any { lower.endsWith(it) }
    }
}
