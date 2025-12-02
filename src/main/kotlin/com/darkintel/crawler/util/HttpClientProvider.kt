package com.darkintel.crawler.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientProvider {

    // 애플리케이션 전역에서 공유하는 단일 HttpClient 인스턴스
    val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                        isLenient = true
                    }
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }

            install(Logging) {
                // 현재는 기본 로깅 설정을 그대로 사용한다.
            }

            defaultRequest {
                // 공통 User-Agent 헤더 설정
                header("User-Agent", "darkweb-crawler/0.1")
            }
        }
    }
}
