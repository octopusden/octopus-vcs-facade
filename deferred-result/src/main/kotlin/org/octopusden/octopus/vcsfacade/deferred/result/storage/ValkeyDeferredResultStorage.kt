package org.octopusden.octopus.vcsfacade.deferred.result.storage

import org.redisson.api.RedissonClient
import org.redisson.api.bucket.CompareAndSetArgs
import org.redisson.client.codec.StringCodec
import java.time.Duration

class ValkeyDeferredResultStorage(
    private val redissonClient: RedissonClient,
    private val namespace: String,
) : DeferredResultStorage {
    override fun tryStartProcessing(
        requestId: String,
        processingValue: String,
        ttl: Duration,
    ): Boolean = bucket(requestId).setIfAbsent(processingValue, ttl)

    override fun completeProcessing(
        requestId: String,
        expectedValue: String,
        completedValue: String,
        ttl: Duration,
    ): Boolean =
        bucket(requestId)
            .compareAndSet(
                CompareAndSetArgs
                    .expected(expectedValue)
                    .set(completedValue)
                    .timeToLive(ttl),
            )

    override fun cancelProcessing(
        requestId: String,
        expectedValue: String,
    ) {
        bucket(requestId).compareAndSet(expectedValue, null)
    }

    override fun find(requestId: String): String? = bucket(requestId).get()

    private fun bucket(requestId: String) =
        redissonClient
            .getBucket<String>(key(requestId), StringCodec.INSTANCE)

    private fun key(requestId: String) = "$namespace:$requestId"
}
