package org.octopusden.octopus.vcsfacade.deferred.result.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus
import org.octopusden.octopus.vcsfacade.deferred.result.serialize.JacksonDeferredResultSerializer
import org.octopusden.octopus.vcsfacade.deferred.result.storage.DeferredResultStorage
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultDeferredResultManagerTest {
    @Test
    fun releasesMarkerWhenOwnershipVerificationFails() {
        val storage =
            FakeStorage(
                renewalError = IllegalStateException("valkey unavailable"),
            )
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        try {
            val executions = AtomicInteger()
            val manager = newManager(storage, scheduler)

            val result =
                manager.execute("request", String::class.java) {
                    executions.incrementAndGet()
                    "value"
                }

            assertEquals(DeferredResultStatus.PROCESSING, result.status)
            assertEquals(0, executions.get())
            assertNotNull(storage.cancelledValue)
            assertEquals(storage.startedValue, storage.cancelledValue)
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun doesNotReleaseMarkerWhenOwnershipIsLost() {
        val storage = FakeStorage(renewalResult = false)
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        try {
            val executions = AtomicInteger()
            val manager = newManager(storage, scheduler)

            val result =
                manager.execute("request", String::class.java) {
                    executions.incrementAndGet()
                    "value"
                }

            assertEquals(DeferredResultStatus.PROCESSING, result.status)
            assertEquals(0, executions.get())
            assertNull(storage.cancelledValue)
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun newManager(
        storage: DeferredResultStorage,
        scheduler: ScheduledExecutorService,
    ): DeferredResultManager =
        DefaultDeferredResultManager(
            deferredResultStorage = storage,
            serializer = JacksonDeferredResultSerializer(jacksonObjectMapper()),
            executor = Executor { it.run() },
            renewalScheduler = scheduler,
            processingTtl = Duration.ofSeconds(30),
            resultTtl = Duration.ofSeconds(30),
        )

    private class FakeStorage(
        private val renewalError: Exception? = null,
        private val renewalResult: Boolean = true,
    ) : DeferredResultStorage {
        var startedValue: String? = null
        var cancelledValue: String? = null

        override fun tryStartProcessing(
            requestId: String,
            processingValue: String,
            ttl: Duration,
        ): Boolean {
            startedValue = processingValue
            return true
        }

        override fun renewProcessing(
            requestId: String,
            expectedValue: String,
            ttl: Duration,
        ): Boolean {
            renewalError?.let { throw it }
            return renewalResult
        }

        override fun completeProcessing(
            requestId: String,
            expectedValue: String,
            completedValue: String,
            ttl: Duration,
        ): Boolean = false

        override fun cancelProcessing(
            requestId: String,
            expectedValue: String,
        ) {
            cancelledValue = expectedValue
        }

        override fun find(requestId: String): String? = null
    }
}
