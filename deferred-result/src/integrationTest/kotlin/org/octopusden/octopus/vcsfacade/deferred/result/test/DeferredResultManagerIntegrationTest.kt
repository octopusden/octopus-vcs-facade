package org.octopusden.octopus.vcsfacade.deferred.result.test

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.octopusden.octopus.vcsfacade.deferred.result.core.DefaultDeferredResultManager
import org.octopusden.octopus.vcsfacade.deferred.result.core.DeferredResultManager
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultException
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.PublishRetryPolicy
import org.octopusden.octopus.vcsfacade.deferred.result.serialize.JacksonDeferredResultSerializer
import org.octopusden.octopus.vcsfacade.deferred.result.storage.ValkeyDeferredResultStorage
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DeferredResultManagerIntegrationTest {
    @Test
    @Timeout(60)
    fun suppressesConcurrentExecutionsAcrossInstances() {
        val requestId = newRequestId()
        val executions = redisson.getAtomicLong(execKey(requestId))
        val instances = List(INSTANCES) { newManager() }
        val operationStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callers = Executors.newFixedThreadPool(CALLERS)
        val start = CountDownLatch(1)

        try {
            val futures = (0 until CALLERS).map { index ->
                callers.submit {
                    start.await()
                    instances[index % instances.size].execute(requestId, String::class.java) {
                        executions.incrementAndGet()
                        operationStarted.countDown()
                        release.await(20, TimeUnit.SECONDS)
                        "value"
                    }
                }
            }

            start.countDown()

            assertTrue(operationStarted.await(10, TimeUnit.SECONDS))

            await().atMost(Duration.ofSeconds(10)).until {
                instances.all { it.get<String>(requestId, String::class.java)?.status == DeferredResultStatus.PROCESSING }
            }

            release.countDown()

            await().atMost(Duration.ofSeconds(10)).until {
                instances.first().get<String>(requestId, String::class.java)?.status == DeferredResultStatus.COMPLETED
            }

            assertEquals(1L, executions.get())
            assertEquals("value", instances.first().get<String>(requestId, String::class.java)?.result)

            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            release.countDown()
            callers.shutdownNow()
        }
    }

    @Test
    @Timeout(60)
    fun suppressesConcurrentExecutionsAcrossIndependentClients() {
        val requestId = newRequestId()
        val clients = List(INSTANCES) { Redisson.create(redissonConfig()) }
        val instances = clients.map { newManager(redissonClient = it) }
        val executions = AtomicInteger()
        val operationStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callers = Executors.newFixedThreadPool(CALLERS)
        val start = CountDownLatch(1)

        try {
            val futures = (0 until CALLERS).map { index ->
                callers.submit {
                    start.await()
                    instances[index % instances.size].execute(requestId, String::class.java) {
                        executions.incrementAndGet()
                        operationStarted.countDown()
                        release.await(20, TimeUnit.SECONDS)
                        "value"
                    }
                }
            }

            start.countDown()

            assertTrue(operationStarted.await(10, TimeUnit.SECONDS))

            await().atMost(Duration.ofSeconds(10)).until {
                instances.all { it.get<String>(requestId, String::class.java)?.status == DeferredResultStatus.PROCESSING }
            }

            release.countDown()

            await().atMost(Duration.ofSeconds(10)).until {
                instances.all { it.get<String>(requestId, String::class.java)?.status == DeferredResultStatus.COMPLETED }
            }

            assertEquals(1, executions.get())
            assertEquals("value", instances.first().get<String>(requestId, String::class.java)?.result)

            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            release.countDown()
            callers.shutdownNow()
            clients.forEach(RedissonClient::shutdown)
        }
    }

    @Test
    @Timeout(60)
    fun expiredAttemptCannotOverwriteNewResult() {
        val requestId = newRequestId()
        val pending = mutableListOf<Runnable>()
        val stale = newManager(processingTtl = Duration.ofMillis(200), executor = Executor { pending.add(it) })
        val current = newManager(executor = Executor { it.run() })
        stale.execute<String>(requestId, String::class.java) { throw AssertionError("Stale operation ran") }
        bucket(requestId).delete()
        current.execute(requestId, String::class.java) { "fresh" }
        pending.single().run()
        assertEquals("fresh", current.get<String>(requestId, String::class.java)?.result)
    }

    @Test
    @Timeout(60)
    fun reExecutesAfterResultTtlExpires() {
        val requestId = newRequestId()
        val executions = redisson.getAtomicLong(execKey(requestId))
        val manager = newManager(resultTtl = Duration.ofSeconds(1), executor = Executor { it.run() })

        manager.execute(requestId, String::class.java) {
            executions.incrementAndGet()
            "first"
        }
        assertEquals(DeferredResultStatus.COMPLETED, manager.get<String>(requestId, String::class.java)?.status)

        await().atMost(Duration.ofSeconds(10)).until { manager.get<String>(requestId, String::class.java) == null }

        manager.execute(requestId, String::class.java) {
            executions.incrementAndGet()
            "second"
        }
        val result = manager.get<String>(requestId, String::class.java)
        assertEquals(DeferredResultStatus.COMPLETED, result?.status)
        assertEquals("second", result?.result)
        assertEquals(2L, executions.get())
    }

    @Test
    @Timeout(60)
    fun returnsResultProducedByAnotherInstance() {
        val requestId = newRequestId()
        val producer = newManager(executor = Executor { it.run() })
        val consumer = newManager()

        producer.execute(requestId, String::class.java) { "value" }

        assertNull(consumer.get<String>(newRequestId(), String::class.java))
        assertEquals("value", consumer.get<String>(requestId, String::class.java)?.result)
    }

    @Test
    fun tryStartProcessingCreatesMarkerWithValueAndLease() {
        val requestId = newRequestId()
        val processingValue = newProcessingValue()
        val lease = Duration.ofSeconds(30)

        assertTrue(storage.tryStartProcessing(requestId, processingValue, lease))

        val bucket = bucket(requestId)
        assertEquals(processingValue, bucket.get())
        assertTrue(bucket.remainTimeToLive() in 1L..lease.toMillis())
    }

    @Test
    fun secondValueCannotStartProcessing() {
        val requestId = newRequestId()

        assertTrue(storage.tryStartProcessing(requestId, newProcessingValue(), Duration.ofSeconds(30)))
        assertFalse(storage.tryStartProcessing(requestId, newProcessingValue(), Duration.ofSeconds(30)))
    }

    @Test
    fun completeProcessingStoresResultForOwner() {
        val requestId = newRequestId()
        val processingValue = newProcessingValue()
        val completedValue = newCompletedValue()
        val ttl = Duration.ofSeconds(30)

        assertTrue(storage.tryStartProcessing(requestId, processingValue, Duration.ofSeconds(30)))
        assertTrue(storage.completeProcessing(requestId, processingValue, completedValue, ttl))

        assertEquals(completedValue, storage.find(requestId))
        assertTrue(bucket(requestId).remainTimeToLive() in 1L..ttl.toMillis())
    }

    @Test
    fun staleValueCannotOverwriteCompletedResult() {
        val requestId = newRequestId()
        val staleValue = newProcessingValue()
        val currentValue = newProcessingValue()
        val completedValue = newCompletedValue()

        assertTrue(storage.tryStartProcessing(requestId, staleValue, Duration.ofMillis(100)))

        await().atMost(Duration.ofSeconds(10)).until { storage.find(requestId) == null }

        assertTrue(storage.tryStartProcessing(requestId, currentValue, Duration.ofSeconds(30)))
        assertTrue(storage.completeProcessing(requestId, currentValue, completedValue, Duration.ofSeconds(30)))

        assertFalse(storage.completeProcessing(requestId, staleValue, newCompletedValue(), Duration.ofSeconds(30)))
        assertEquals(completedValue, storage.find(requestId))
    }

    @Test
    fun renewProcessingExtendsLeaseForOwner() {
        val requestId = newRequestId()
        val processingValue = newProcessingValue()

        assertTrue(storage.tryStartProcessing(requestId, processingValue, Duration.ofSeconds(2)))
        Thread.sleep(700)
        val remainingBefore = bucket(requestId).remainTimeToLive()

        assertTrue(storage.renewProcessing(requestId, processingValue, Duration.ofSeconds(5)))
        val remainingAfter = bucket(requestId).remainTimeToLive()

        assertEquals(processingValue, bucket(requestId).get())
        assertTrue(remainingAfter > remainingBefore)
        assertTrue(remainingAfter in 1L..Duration.ofSeconds(5).toMillis())
    }

    @Test
    fun renewProcessingFailsAfterMarkerReplaced() {
        val requestId = newRequestId()
        val firstValue = newProcessingValue()
        val secondValue = newProcessingValue()

        assertTrue(storage.tryStartProcessing(requestId, firstValue, Duration.ofSeconds(30)))
        storage.cancelProcessing(requestId, firstValue)
        assertTrue(storage.tryStartProcessing(requestId, secondValue, Duration.ofSeconds(30)))

        assertFalse(storage.renewProcessing(requestId, firstValue, Duration.ofSeconds(30)))
        assertEquals(secondValue, bucket(requestId).get())
    }

    @Test
    fun renewProcessingFailsAfterMarkerExpires() {
        val requestId = newRequestId()
        val processingValue = newProcessingValue()

        assertTrue(storage.tryStartProcessing(requestId, processingValue, Duration.ofMillis(100)))
        await().atMost(Duration.ofSeconds(10)).until { storage.find(requestId) == null }

        assertFalse(storage.renewProcessing(requestId, processingValue, Duration.ofSeconds(30)))
    }

    @Test
    fun cancelProcessingRemovesMarkerForOwner() {
        val requestId = newRequestId()
        val processingValue = newProcessingValue()

        assertTrue(storage.tryStartProcessing(requestId, processingValue, Duration.ofSeconds(30)))
        storage.cancelProcessing(requestId, processingValue)

        assertFalse(bucket(requestId).isExists)
        assertNull(storage.find(requestId))
    }

    @Test
    fun foreignValueCannotRemoveMarker() {
        val requestId = newRequestId()
        val processingValue = newProcessingValue()

        assertTrue(storage.tryStartProcessing(requestId, processingValue, Duration.ofSeconds(30)))
        storage.cancelProcessing(requestId, newProcessingValue())

        val bucket = bucket(requestId)
        assertTrue(bucket.isExists)
        assertEquals(processingValue, bucket.get())
    }

    @Test
    fun findReturnsNullForMissingResult() {
        assertEquals(null, storage.find(newRequestId()))
    }

    @Test
    fun processingMarkerExpiresAfterLease() {
        val requestId = newRequestId()

        assertTrue(
            storage.tryStartProcessing(
                requestId,
                newProcessingValue(),
                Duration.ofMillis(100),
            ),
        )

        await().atMost(Duration.ofSeconds(10)).until { storage.find(requestId) == null }

        assertNull(storage.find(requestId))
    }

    @Test
    fun anotherValueCanStartProcessingAfterLeaseExpires() {
        val requestId = newRequestId()
        val firstValue = newProcessingValue()
        val secondValue = newProcessingValue()

        assertTrue(
            storage.tryStartProcessing(
                requestId,
                firstValue,
                Duration.ofMillis(100),
            ),
        )

        await().atMost(Duration.ofSeconds(10)).until { storage.find(requestId) == null }

        assertTrue(
            storage.tryStartProcessing(
                requestId,
                secondValue,
                Duration.ofSeconds(30),
            ),
        )

        assertEquals(secondValue, bucket(requestId).get())
    }

    @Test
    fun onlyOneValueCanStartProcessingConcurrently() {
        val requestId = newRequestId()
        val values = List(10) { newProcessingValue() }
        val executor = Executors.newFixedThreadPool(values.size)
        val ready = CountDownLatch(values.size)
        val start = CountDownLatch(1)

        try {
            val futures = values.map { processingValue ->
                executor.submit<Boolean> {
                    ready.countDown()
                    start.await()

                    storage.tryStartProcessing(
                        requestId = requestId,
                        processingValue = processingValue,
                        ttl = Duration.ofSeconds(30),
                    )
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))

            start.countDown()

            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it })

            val successfulValue = values[results.indexOf(true)]

            assertEquals(successfulValue, bucket(requestId).get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun executesOperationOnceAndCachesResultWithoutReExecuting() {
        val requestId = newRequestId()
        val calls = AtomicInteger()
        val manager = newManager(executor = Executor { it.run() })

        val first = manager.execute(requestId, String::class.java) {
            calls.incrementAndGet()
            "value"
        }
        assertEquals(DeferredResultStatus.PROCESSING, first.status)

        val completed = manager.get<String>(requestId, String::class.java)
        assertEquals(DeferredResultStatus.COMPLETED, completed?.status)
        assertEquals("value", completed?.result)

        val cached = manager.execute(requestId, String::class.java) {
            calls.incrementAndGet()
            "other"
        }
        assertEquals(DeferredResultStatus.COMPLETED, cached.status)
        assertEquals("value", cached.result)
        assertEquals(1, calls.get())
    }

    @Test
    fun secondExecuteReturnsProcessingWithoutSchedulingDuplicate() {
        val requestId = newRequestId()
        val pending = ConcurrentLinkedQueue<Runnable>()
        val manager = newManager(executor = Executor { pending.add(it) })

        val first = manager.execute(requestId, String::class.java) { "value" }
        val second = manager.execute(requestId, String::class.java) { "other" }

        assertEquals(DeferredResultStatus.PROCESSING, first.status)
        assertEquals(DeferredResultStatus.PROCESSING, second.status)
        assertEquals(1, pending.size)
        assertEquals(DeferredResultStatus.PROCESSING, manager.get<String>(requestId, String::class.java)?.status)

        pending.single().run()

        assertEquals("value", manager.get<String>(requestId, String::class.java)?.result)
    }

    @Test
    fun storesFailedStatusWhenOperationThrows() {
        val requestId = newRequestId()
        val manager = newManager(executor = Executor { it.run() })

        manager.execute<String>(requestId, String::class.java) { error("boom") }
        val failed = manager.get<String>(requestId, String::class.java)

        assertEquals(DeferredResultStatus.FAILED, failed?.status)
        assertEquals("java.lang.IllegalStateException", failed?.error?.code)
        assertEquals("boom", failed?.error?.message)
        assertNull(failed?.result)
    }

    @Test
    fun rethrowsOperationErrorAndKeepsProcessingMarker() {
        val requestId = newRequestId()
        val manager = newManager(executor = Executor { it.run() })

        assertThrows(AssertionError::class.java) {
            manager.execute<String>(requestId, String::class.java) { throw AssertionError("boom") }
        }

        assertEquals(DeferredResultStatus.PROCESSING, manager.get<String>(requestId, String::class.java)?.status)
    }

    @Test
    fun failsFastWhenStoredResultTypeMismatchesOnGet() {
        val requestId = newRequestId()
        val manager = newManager(executor = Executor { it.run() })

        manager.execute(requestId, String::class.java) { "value" }

        assertThrows(DeferredResultException::class.java) {
            manager.get<Int>(requestId, Int::class.java)
        }
    }

    @Test
    fun failsFastWhenStoredResultTypeMismatchesOnExecute() {
        val requestId = newRequestId()
        val calls = AtomicInteger()
        val manager = newManager(executor = Executor { it.run() })

        manager.execute(requestId, String::class.java) { "value" }

        assertThrows(DeferredResultException::class.java) {
            manager.execute(requestId, Int::class.java) {
                calls.incrementAndGet()
                42
            }
        }
        assertEquals(0, calls.get())
    }

    @Test
    fun supportsParameterizedResultTypes() {
        val requestId = newRequestId()
        val type = object : TypeReference<List<String>>() {}.type
        val manager = newManager(executor = Executor { it.run() })

        manager.execute(requestId, type) { listOf("a", "b") }
        val completed = manager.get<List<String>>(requestId, type)

        assertEquals(listOf("a", "b"), completed?.result)
    }

    @Test
    fun supportsUnitResult() {
        val requestId = newRequestId()
        val manager = newManager(executor = Executor { it.run() })

        manager.execute(requestId, Unit::class.java) { Unit }

        val completed = manager.get<Unit>(requestId, Unit::class.java)

        assertEquals(DeferredResultStatus.COMPLETED, completed?.status)
        assertEquals(Unit, completed?.result)
    }

    @Test
    fun cancelsProcessingMarkerWhenExecutorRejects() {
        val requestId = newRequestId()
        val manager = newManager(executor = Executor { throw IllegalStateException("rejected") })

        assertThrows(IllegalStateException::class.java) {
            manager.execute<String>(requestId, String::class.java) { "value" }
        }

        assertNull(storage.find(requestId))
    }

    @Test
    fun cachedFailedResultIsNotReExecuted() {
        val requestId = newRequestId()
        val calls = AtomicInteger()
        val manager = newManager(executor = Executor { it.run() })

        val first = manager.execute<String>(requestId, String::class.java) {
            calls.incrementAndGet()
            error("boom")
        }
        assertEquals(DeferredResultStatus.PROCESSING, first.status)

        val cached = manager.execute<String>(requestId, String::class.java) {
            calls.incrementAndGet()
            error("not reached")
        }
        assertEquals(DeferredResultStatus.FAILED, cached.status)
        assertEquals("boom", cached.error?.message)
        assertEquals(1, calls.get())
    }

    @Test
    fun failedResultIsVisibleToAnotherInstance() {
        val requestId = newRequestId()
        val producer = newManager(executor = Executor { it.run() })
        val consumer = newManager()

        producer.execute<String>(requestId, String::class.java) { error("boom") }

        val failed = consumer.get<String>(requestId, String::class.java)
        assertEquals(DeferredResultStatus.FAILED, failed?.status)
        assertEquals("java.lang.IllegalStateException", failed?.error?.code)
        assertEquals("boom", failed?.error?.message)
    }

    @Test
    fun storesDefaultMessageWhenOperationThrowsWithoutMessage() {
        val requestId = newRequestId()
        val manager = newManager(executor = Executor { it.run() })

        manager.execute<String>(requestId, String::class.java) { throw IllegalArgumentException() }

        val failed = manager.get<String>(requestId, String::class.java)
        assertEquals(DeferredResultStatus.FAILED, failed?.status)
        assertEquals("java.lang.IllegalArgumentException", failed?.error?.code)
        assertEquals("Execution failed", failed?.error?.message)
    }

    @Test
    @Timeout(60)
    fun renewsProcessingWhileQueuedAndRunning() {
        val requestId = newRequestId()
        val pending = ConcurrentLinkedQueue<Runnable>()
        val manager = newManager(
            processingTtl = Duration.ofMillis(200),
            executor = Executor { pending.add(it) },
        )

        val other = newManager(executor = Executor { it.run() })

        fun retry() {
            val result = other.execute<String>(requestId, String::class.java) { throw AssertionError("Duplicate execution") }
            assertEquals(DeferredResultStatus.PROCESSING, result.status)
        }

        manager.execute(requestId, String::class.java) {
            Thread.sleep(700)
            retry()
            "first"
        }
        Thread.sleep(700)
        retry()
        pending.single().run()
        assertEquals("first", manager.get<String>(requestId, String::class.java)?.result)
    }

    @Test
    @Timeout(60)
    fun resultTtlStartsAtCompletion() {
        val requestId = newRequestId()
        val pending = ConcurrentLinkedQueue<Runnable>()
        val manager = newManager(
            processingTtl = Duration.ofSeconds(3),
            resultTtl = Duration.ofSeconds(1),
            executor = { pending.add(it) },
        )

        manager.execute(requestId, String::class.java) { "value" }

        Thread.sleep(2000)
        pending.single().run()

        assertEquals("value", manager.get<String>(requestId, String::class.java)?.result)

        await().atMost(Duration.ofSeconds(10)).until { manager.get<String>(requestId, String::class.java) == null }
    }

    private fun newManager(
        processingTtl: Duration = Duration.ofSeconds(30),
        resultTtl: Duration = RESULT_TTL,
        cancelRetryInterval: Duration = Duration.ofSeconds(10),
        executor: Executor = operationExecutor,
        publishRetry: PublishRetryPolicy = PublishRetryPolicy(maxAttempts = 3, delay = Duration.ofMillis(50)),
        redissonClient: RedissonClient = redisson,
    ): DeferredResultManager =
        DefaultDeferredResultManager(
            deferredResultStorage = ValkeyDeferredResultStorage(redissonClient, NAMESPACE),
            serializer = JacksonDeferredResultSerializer(objectMapper),
            executor = executor,
            renewalScheduler = renewalScheduler,
            processingTtl = processingTtl,
            resultTtl = resultTtl,
            cancelRetryInterval = cancelRetryInterval,
            publishRetry = publishRetry,
        )

    private fun bucket(requestId: String) = redisson.getBucket<String>(key(requestId), StringCodec.INSTANCE)

    private fun newRequestId() = UUID.randomUUID().toString()

    private fun newProcessingValue() = "PROCESSING:${UUID.randomUUID()}"

    private fun newCompletedValue() = "COMPLETED:${UUID.randomUUID()}"

    private fun execKey(requestId: String) = "$NAMESPACE:exec:$requestId"

    private fun key(requestId: String) = "$NAMESPACE:$requestId"

    companion object {
        private const val NAMESPACE = "deferred-result-it"
        private const val INSTANCES = 4
        private const val CALLERS = 32
        private val RESULT_TTL = Duration.ofSeconds(30)

        private val objectMapper = jacksonObjectMapper()

        private lateinit var redisson: RedissonClient
        private lateinit var storage: ValkeyDeferredResultStorage
        private val renewalScheduler = Executors.newSingleThreadScheduledExecutor()
        private lateinit var operationExecutor: ExecutorService

        @BeforeAll
        @JvmStatic
        fun setUp() {
            redisson = Redisson.create(redissonConfig())
            storage = ValkeyDeferredResultStorage(redisson, NAMESPACE)
            operationExecutor = Executors.newCachedThreadPool()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            if (::redisson.isInitialized) {
                redisson.keys.deleteByPattern("$NAMESPACE:*")
            }
            renewalScheduler.shutdownNow()
            if (::operationExecutor.isInitialized) {
                operationExecutor.shutdownNow()
            }
            if (::redisson.isInitialized) {
                redisson.shutdown()
            }
        }

        private fun redissonConfig(): Config {
            val host = System.getProperty("test.valkey-host")
                ?: error("System property 'test.valkey-host' is not set")

            val config = Config()
            config.useSingleServer().setAddress("valkey://$host")
            return config
        }
    }
}
