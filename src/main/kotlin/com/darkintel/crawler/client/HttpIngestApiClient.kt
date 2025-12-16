package com.darkintel.crawler.client

import com.darkintel.crawler.model.IngestRawRequest
import com.darkintel.crawler.model.NormalizedDocument
import com.darkintel.crawler.util.HttpClientProvider
import com.darkintel.crawler.util.getLogger
import com.darkintel.crawler.util.retry
import com.darkintel.crawler.error.RetriableCrawlerException
import com.darkintel.crawler.error.FatalCrawlerException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.plugins.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class HttpIngestApiClient(
    private val backendBaseUrl: String,
    private val backendApiToken: String
) : IngestApiClient {

    private val client = HttpClientProvider.client
    private val logger = getLogger(HttpIngestApiClient::class)

    override suspend fun send(doc: NormalizedDocument) {
        val collectedAtIso = doc.collectedAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val requestBody = IngestRawRequest(
            sourceId = doc.sourceId,
            title = doc.title,
            url = doc.url,
            contentText = doc.contentText,
            attachmentUrls = doc.attachmentUrls,
            rawMeta = doc.rawMeta,
            collectedAt = collectedAtIso
        )

        val url = backendBaseUrl.trimEnd('/') + "/ingest/raw"

        retry {
            try {
                val response: HttpResponse = client.post(url) {
                    header("Authorization", "Bearer $backendApiToken")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
                // 리소스 해제를 보장하기 위해 응답 본문을(선택적으로) 소비한다
                response.body<String>()
                logger.info("Ingest API accepted document for source {}: {}", doc.sourceId, doc.url)
            } catch (e: ClientRequestException) { // 4xx 응답 처리
                val code = e.response.status.value
                if (code == 401) {
                    throw FatalCrawlerException("Unauthorized from ingest API (401)", e)
                } else {
                    // 명시적으로 구분한 경우를 제외한 다른 4xx는 재시도 가능한 오류로 취급하는 보수적인 방식
                    throw RetriableCrawlerException("HTTP ${'$'}code from ingest API", e)
                }
            } catch (e: ServerResponseException) { // 5xx 응답 처리
                throw RetriableCrawlerException("5xx from ingest API", e)
            } catch (e: HttpRequestTimeoutException) {
                throw RetriableCrawlerException("HTTP request timeout to ingest API", e)
            } catch (e: ConnectTimeoutException) {
                throw RetriableCrawlerException("HTTP connect timeout to ingest API", e)
            }
        }
    }
}
