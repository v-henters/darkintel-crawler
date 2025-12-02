package com.darkintel.crawler.config

import com.moandjiezana.toml.Toml
import java.io.File

object ConfigLoader {
    fun load(path: String = "config.toml"): AppConfig {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Config file not found at path: $path")
        }

        val toml = Toml().read(file)

        val backendBaseUrl = toml.getString("backend_base_url")
            ?: throw IllegalArgumentException("Missing 'backend_base_url' in config")
        val backendApiToken = toml.getString("backend_api_token")
            ?: throw IllegalArgumentException("Missing 'backend_api_token' in config")

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

        return AppConfig(
            backendBaseUrl = backendBaseUrl,
            backendApiToken = backendApiToken,
            scheduler = scheduler,
            sources = sources
        )
    }
}
