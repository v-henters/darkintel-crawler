package com.darkintel.crawler.redis

interface DistributedLockManager {
    /**
     * 주어진 소스에 대한 락을 획득을 시도한다.
     *
     * @return 락 획득에 성공하면 true, 이미 다른 인스턴스가 보유 중이면 false
     */
    suspend fun tryLock(sourceId: String): Boolean

    /**
     * 이전에 획득했던 소스에 대한 락을 해제한다.
     */
    suspend fun release(sourceId: String)
}
