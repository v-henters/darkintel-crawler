package com.darkintel.crawler.config

data class DynamoDbConfig(
    val sourceStateTable: String,
    val documentsTable: String,
    val locksTable: String,
    val scheduleTable: String
)
