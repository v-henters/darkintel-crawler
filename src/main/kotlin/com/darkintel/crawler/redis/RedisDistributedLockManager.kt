package com.darkintel.crawler.redis

import io.lettuce.core.SetArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class RedisDistributedLockManager(
    private val lockTtlSeconds: Long = 60
) : DistributedLockManager {

    private val syncCommands = RedisClientProvider.syncCommands
    private val instanceId: String = UUID.randomUUID().toString()

    override suspend fun tryLock(sourceId: String): Boolean = withContext(Dispatchers.IO) {
        val key = "lock:$sourceId"
        val args = SetArgs().nx().ex(lockTtlSeconds)

        val result = syncCommands.set(key, instanceId, args)
        // Redis SET NX 명령은 설정에 성공하면 "OK", 실패하면 null을 반환한다.
        return@withContext result == "OK"
    }

    override suspend fun release(sourceId: String) = withContext(Dispatchers.IO) {
        val key = "lock:$sourceId"

        // 현재 값이 이 인스턴스의 instanceId와 일치할 때만 삭제하여, 다른 인스턴스의 락을 지우지 않도록 한다.
        val currentValue = syncCommands.get(key)
        if (currentValue == instanceId) {
            syncCommands.del(key)
        }
    }
}
