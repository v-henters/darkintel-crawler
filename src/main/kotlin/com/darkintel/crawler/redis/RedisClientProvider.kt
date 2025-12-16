package com.darkintel.crawler.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

object RedisClientProvider {

    @Volatile
    private var _client: RedisClient? = null

    val client: RedisClient
        get() = _client ?: error("RedisClientProvider not initialized. Call init() first.")

    val connection: StatefulRedisConnection<String, String> by lazy {
        client.connect()
    }

    val syncCommands: RedisCommands<String, String> by lazy {
        connection.sync()
    }

    fun init(redisUri: String) {
        if (_client != null) return
        _client = RedisClient.create(redisUri)
    }
}
