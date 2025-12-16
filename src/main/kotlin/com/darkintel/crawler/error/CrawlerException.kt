package com.darkintel.crawler.error

open class CrawlerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class RetriableCrawlerException(message: String, cause: Throwable? = null) : CrawlerException(message, cause)

class FatalCrawlerException(message: String, cause: Throwable? = null) : CrawlerException(message, cause)
