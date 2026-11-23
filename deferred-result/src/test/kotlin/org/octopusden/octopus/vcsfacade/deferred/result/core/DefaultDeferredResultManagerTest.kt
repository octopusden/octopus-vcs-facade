package org.octopusden.octopus.vcsfacade.deferred.result.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus
import org.octopusden.octopus.vcsfacade.deferred.result.serialize.JacksonDeferredResultSerializer
import org.octopusden.octopus.vcsfacade.deferred.result.storage.DeferredResultStorage
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Delayed
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultDeferredResultManagerTest {
    @Test
    fun cancelsMarkerAndAllowsRetryToExecute() {
        val storage = FakeStorage(renewalError = IllegalStateException("valkey unavailable"))
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        try {
            val executions = AtomicInteger()
            val manager = newManager(storage, scheduler)

            val first =
                manager.execute("request", String::class.java) {
                    executions.incrementAndGet()
                    "value"
                }

            assertEquals(DeferredResultStatus.PROCESSING, first.status)
            assertEquals(0, executions.get())
            assertEquals(1, storage.cancelCount)
            assertFalse(storage.hasMarker("request"))

            storage.renewalError = null

            manager.execute("request", String::class.java) {
                executions.incrementAndGet()
                "value"
            }

            assertEquals(1, executions.get())
            assertEquals(2, storage.startCount)
            val completed = manager.get<String>("request", String::class.java)
            assertEquals(DeferredResultStatus.COMPLETED, completed?.status)
            assertEquals("value", completed?.result)
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun doesNotCancelMarkerWhenOwnershipIsLost() {
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
            assertEquals(0, storage.cancelCount)
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun retriesCancellationUntilMarkerReleased() {
        val storage =
            FakeStorage(renewalError = IllegalStateException("valkey unavailable")).apply {
                cancelFailuresRemaining = 1
            }
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        try {
            val manager = newManager(storage, scheduler, processingTtl = Duration.ofMillis(300))

            manager.execute("request", String::class.java) { "value" }

            assertTrue(storage.awaitCancellation(5, TimeUnit.SECONDS))
            assertFalse(storage.hasMarker("request"))
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun usesConfiguredCancelRetryIntervalAndRetriesAcrossFailures() {
        val storage =
            FakeStorage(renewalError = IllegalStateException("valkey unavailable")).apply {
                cancelFailuresRemaining = 3
            }
        val scheduler = ManualScheduler()

        try {
            val manager =
                newManager(
                    storage,
                    scheduler,
                    processingTtl = Duration.ofMillis(600),
                    cancelRetryInterval = Duration.ofMillis(100),
                )

            manager.execute("request", String::class.java) { "value" }

            assertEquals(2, scheduler.recorded.size)
            val heartbeat = scheduler.recorded[0]
            val cancelRetry = scheduler.recorded[1]
            assertEquals(200, heartbeat.unit.toMillis(heartbeat.delay))
            assertEquals(100, cancelRetry.unit.toMillis(cancelRetry.initialDelay))
            assertEquals(100, cancelRetry.unit.toMillis(cancelRetry.delay))
            assertEquals(0, cancelRetry.future.cancelCount)

            cancelRetry.command.run()
            assertEquals(0, cancelRetry.future.cancelCount)
            assertTrue(storage.hasMarker("request"))

            cancelRetry.command.run()
            assertEquals(0, cancelRetry.future.cancelCount)
            assertTrue(storage.hasMarker("request"))

            cancelRetry.command.run()
            assertEquals(1, cancelRetry.future.cancelCount)
            assertFalse(storage.hasMarker("request"))
            assertTrue(storage.awaitCancellation(1, TimeUnit.MILLISECONDS))
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun stopsRetryingCancellationAfterLeaseBudget() {
        val storage =
            FakeStorage(renewalError = IllegalStateException("valkey unavailable")).apply {
                cancelFailuresRemaining = 100
            }
        val scheduler = ManualScheduler()

        try {
            val manager =
                newManager(
                    storage,
                    scheduler,
                    processingTtl = Duration.ofMillis(600),
                    cancelRetryInterval = Duration.ofMillis(100),
                )

            manager.execute("request", String::class.java) { "value" }

            val cancelRetry = scheduler.recorded[1]

            repeat(5) {
                cancelRetry.command.run()
                assertEquals(0, cancelRetry.future.cancelCount)
            }

            cancelRetry.command.run()
            assertEquals(1, cancelRetry.future.cancelCount)
            assertTrue(storage.hasMarker("request"))
        } finally {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun newManager(
        storage: DeferredResultStorage,
        scheduler: ScheduledExecutorService,
        processingTtl: Duration = Duration.ofSeconds(30),
        cancelRetryInterval: Duration = Duration.ofMillis(100),
    ): DeferredResultManager =
        DefaultDeferredResultManager(
            deferredResultStorage = storage,
            serializer = JacksonDeferredResultSerializer(jacksonObjectMapper()),
            executor = Executor { it.run() },
            renewalScheduler = scheduler,
            processingTtl = processingTtl,
            resultTtl = Duration.ofSeconds(30),
            cancelRetryInterval = cancelRetryInterval,
        )

    private class FakeStorage(
        var renewalError: Exception? = null,
        var renewalResult: Boolean = true,
        var cancelFailuresRemaining: Int = 0,
    ) : DeferredResultStorage {
        private val markers = mutableMapOf<String, String>()
        private val results = mutableMapOf<String, String>()
        private val cancellationLatch = CountDownLatch(1)

        var startCount = 0
            private set
        var cancelCount = 0
            private set

        override fun tryStartProcessing(
            requestId: String,
            processingValue: String,
            ttl: Duration,
        ): Boolean {
            if (markers.containsKey(requestId)) {
                return false
            }
            markers[requestId] = processingValue
            startCount++
            return true
        }

        override fun renewProcessing(
            requestId: String,
            expectedValue: String,
            ttl: Duration,
        ): Boolean {
            renewalError?.let { throw it }
            if (!renewalResult) {
                return false
            }
            return markers[requestId] == expectedValue
        }

        override fun completeProcessing(
            requestId: String,
            expectedValue: String,
            completedValue: String,
            ttl: Duration,
        ): Boolean {
            if (markers[requestId] != expectedValue) {
                return false
            }
            markers.remove(requestId)
            results[requestId] = completedValue
            return true
        }

        override fun cancelProcessing(
            requestId: String,
            expectedValue: String,
        ) {
            if (cancelFailuresRemaining > 0) {
                cancelFailuresRemaining--
                error("valkey still unavailable")
            }
            cancelCount++
            if (markers[requestId] == expectedValue) {
                markers.remove(requestId)
            }
            cancellationLatch.countDown()
        }

        override fun find(requestId: String): String? = markers[requestId] ?: results[requestId]

        fun hasMarker(requestId: String): Boolean = markers.containsKey(requestId)

        fun awaitCancellation(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = cancellationLatch.await(timeout, unit)
    }

    private class ManualTask : ScheduledFuture<Any> {
        var cancelCount = 0
            private set

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelCount++
            return true
        }

        override fun isCancelled(): Boolean = cancelCount > 0

        override fun isDone(): Boolean = cancelCount > 0

        override fun get(): Any? = null

        override fun get(
            timeout: Long,
            unit: TimeUnit,
        ): Any? = null

        override fun getDelay(unit: TimeUnit): Long = 0

        override fun compareTo(other: Delayed): Int = 0
    }

    private class ManualScheduler : ScheduledExecutorService by Executors.newSingleThreadScheduledExecutor() {
        val recorded = mutableListOf<RecordedTask>()

        class RecordedTask(
            val command: Runnable,
            val initialDelay: Long,
            val delay: Long,
            val unit: TimeUnit,
            val future: ManualTask = ManualTask(),
        )

        override fun scheduleWithFixedDelay(
            command: Runnable,
            initialDelay: Long,
            delay: Long,
            unit: TimeUnit,
        ): ScheduledFuture<*> {
            val task = RecordedTask(command, initialDelay, delay, unit)
            recorded.add(task)
            return task.future
        }
    }
}
