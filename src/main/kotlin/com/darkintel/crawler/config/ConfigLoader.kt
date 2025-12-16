package com.darkintel.crawler.config

import com.moandjiezana.toml.Toml
import java.io.File
import org.slf4j.LoggerFactory

object ConfigLoader {
    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)

    private fun mask(value: String, visible: Int = 4): String {
        if (value.isEmpty()) return value
        val v = value.take(visible)
        return v + "****"
    }

    fun load(path: String = "config.toml"): AppConfig {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Config file not found at path: $path")
        }

        val toml = Toml().read(file)

        logger.info("Loading config from path: {}", file.absolutePath)

        val backendBaseUrl = toml.getString("backend_base_url")
            ?: throw IllegalArgumentException("Missing 'backend_base_url' in config")
        val backendApiToken = toml.getString("backend_api_token")
            ?: throw IllegalArgumentException("Missing 'backend_api_token' in config")

        // Diagnostics for sensitive values (masked)
        logger.info("Config backend_base_url: {}", backendBaseUrl)
        logger.info(
            "Config backend_api_token (masked): {}",
            mask(backendApiToken)
        )

        // DynamoDB tables (mandatory)
        val dynamodbTable = toml.getTable("dynamodb")
            ?: throw IllegalArgumentException("Missing '[dynamodb]' section in config")
        val sourceStateTable = dynamodbTable.getString("source_state_table")
            ?: throw IllegalArgumentException("Missing 'source_state_table' in [dynamodb]")
        val documentsTable = dynamodbTable.getString("documents_table")
            ?: throw IllegalArgumentException("Missing 'documents_table' in [dynamodb]")
        val locksTable = dynamodbTable.getString("locks_table")
            ?: throw IllegalArgumentException("Missing 'locks_table' in [dynamodb]")
        val scheduleTable = dynamodbTable.getString("schedule_table")
            ?: throw IllegalArgumentException("Missing 'schedule_table' in [dynamodb]")

        val dynamodb = DynamoDbConfig(
            sourceStateTable = sourceStateTable,
            documentsTable = documentsTable,
            locksTable = locksTable,
            scheduleTable = scheduleTable
        )

        logger.info(
            "Config dynamodb tables: source_state_table={}, documents_table={}, locks_table={}, schedule_table={}",
            sourceStateTable,
            documentsTable,
            locksTable,
            scheduleTable
        )

        val schedulerTable = toml.getTable("scheduler")
            ?: throw IllegalArgumentException("Missing '[scheduler]' section in config")
        val concurrency = schedulerTable.getLong("concurrency")?.toInt()
            ?: throw IllegalArgumentException("Missing 'concurrency' in [scheduler]")
        val requestTimeoutSeconds = schedulerTable.getLong("request_timeout_seconds")?.toInt()
            ?: throw IllegalArgumentException("Missing 'request_timeout_seconds' in [scheduler]")
        val scheduler = SchedulerConfig(
            concurrency = concurrency,
            requestTimeoutSeconds = requestTimeoutSeconds
        )

        logger.info(
            "Config scheduler: concurrency={}, request_timeout_seconds={}",
            concurrency,
            requestTimeoutSeconds
        )

        val sourceTables = toml.getTables("sources") ?: emptyList()
        val sources = sourceTables.map { t ->
            val id = t.getString("id")
                ?: throw IllegalArgumentException("Each [[sources]] must have 'id'")
            val name = t.getString("name")
                ?: throw IllegalArgumentException("Each [[sources]] must have 'name'")
            val baseUrl = t.getString("base_url")
                ?: throw IllegalArgumentException("Each [[sources]] must have 'base_url'")
            val parserType = t.getString("parser_type")
            val crawlIntervalMinutes = t.getLong("crawl_interval_minutes")?.toInt()
                ?: throw IllegalArgumentException("Each [[sources]] must have 'crawl_interval_minutes'")

            SourceConfig(
                id = id,
                name = name,
                baseUrl = baseUrl,
                parserType = parserType,
                crawlIntervalMinutes = crawlIntervalMinutes
            )
        }

        logger.info("Config sources loaded: count={}", sources.size)

        return AppConfig(
            backendBaseUrl = backendBaseUrl,
            backendApiToken = backendApiToken,
            dynamodb = dynamodb,
            scheduler = scheduler,
            sources = sources
        )
    }
}
