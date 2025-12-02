package com.darkintel.crawler.config

object RuntimeConfigLoader {

    fun load(args: Array<String>): RuntimeConfig {
        val env = System.getenv()

        val cliConfigPath = args.firstOrNull()
        val envConfigPath = env["DARKWEB_CONFIG_PATH"]
        val configPath = cliConfigPath ?: envConfigPath ?: "config.toml"

        val awsRegion = env["DARKWEB_AWS_REGION"]
        val redisUri = env["DARKWEB_REDIS_URI"]
        val logLevel = env["DARKWEB_LOG_LEVEL"]

        val useRedisRateLimiter = parseBooleanEnv(env, "DARKWEB_USE_REDIS_RATELIMITER", default = false)
        val useRedisLock = parseBooleanEnv(env, "DARKWEB_USE_REDIS_LOCK", default = false)

        return RuntimeConfig(
            configPath = configPath,
            awsRegion = awsRegion,
            redisUri = redisUri,
            logLevel = logLevel,
            useRedisRateLimiter = useRedisRateLimiter,
            useRedisLock = useRedisLock
        )
    }

    private fun parseBooleanEnv(env: Map<String, String>, key: String, default: Boolean): Boolean {
        val raw = env[key] ?: return default
        return raw.equals("true", ignoreCase = true) ||
                raw.equals("1", ignoreCase = true) ||
                raw.equals("yes", ignoreCase = true)
    }
}
