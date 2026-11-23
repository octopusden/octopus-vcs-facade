package org.octopusden.octopus.vcsfacade.deferred.result.storage

import java.time.Duration

interface DeferredResultStorage {
    fun tryStartProcessing(
        requestId: String,
        processingValue: String,
        ttl: Duration,
    ): Boolean

    fun renewProcessing(
        requestId: String,
        expectedValue: String,
        ttl: Duration,
    ): Boolean = completeProcessing(requestId, expectedValue, expectedValue, ttl)

    fun completeProcessing(
        requestId: String,
        expectedValue: String,
        completedValue: String,
        ttl: Duration,
    ): Boolean

    fun cancelProcessing(
        requestId: String,
        expectedValue: String,
    )

    fun find(requestId: String): String?
}
