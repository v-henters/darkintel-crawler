package com.darkintel.crawler.parser

import com.darkintel.crawler.config.SourceConfig
import com.darkintel.crawler.model.NormalizedDocument
import com.darkintel.crawler.model.SourceState

interface Parser {
    /**
     * 전달된 소스를 크롤링하고, 하나 이상의 정규화된 문서를 반환한다.
     *
     * @param source 이 소스에 대한 설정 정보
     * @param state  이 소스의 마지막 상태 정보(없을 수 있음)
     */
    suspend fun crawl(source: SourceConfig, state: SourceState?): List<NormalizedDocument>
}
