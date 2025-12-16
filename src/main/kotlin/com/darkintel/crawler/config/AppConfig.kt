package com.darkintel.crawler.config

data class AppConfig(
    val backendBaseUrl: String,
    val backendApiToken: String,
    val dynamodb: DynamoDbConfig,
    val scheduler: SchedulerConfig,
    val sources: List<SourceConfig>
)
