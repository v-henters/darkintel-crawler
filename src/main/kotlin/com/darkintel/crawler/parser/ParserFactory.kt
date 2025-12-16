package com.darkintel.crawler.parser

import com.darkintel.crawler.config.SourceConfig

object ParserFactory {

    fun create(source: SourceConfig): Parser {
        return when (source.parserType?.uppercase()) {
            "BASIC", null -> BasicParser()
            "RANSOMLIVE" -> RansomLiveParser()
            // "FORUM" -> ForumParser() // 추후 구현 예정
            // "MARKET" -> MarketParser() // 추후 구현 예정
            else -> BasicParser() // 기본 파서로 폴백
        }
    }
}
