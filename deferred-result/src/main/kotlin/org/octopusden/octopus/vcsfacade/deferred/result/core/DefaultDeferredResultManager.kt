package org.octopusden.octopus.vcsfacade.deferred.result.core

import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResult
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultError
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultException
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.PublishRetryPolicy
import org.octopusden.octopus.vcsfacade.deferred.result.serialize.DeferredResultSerializer
import org.octopusden.octopus.vcsfacade.deferred.result.storage.DeferredResultStorage
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class DefaultDeferredResultManager(
    private val deferredResultStorage: DeferredResultStorage,
    private val serializer: DeferredResultSerializer,
    private val executor: Executor,
    private val processingTtl: Duration,
    private val renewalScheduler: ScheduledExecutorService,
    private val resultTtl: Duration,
    private val cancelRetryInterval: Duration,
    private val publishRetry: PublishRetryPolicy = PublishRetryPolicy(),
) : DeferredResultManager {
    private val lease =
        ProcessingLease(
            deferredResultStorage,
            renewalScheduler,
            processingTtl,
            cancelRetryInterval,
        )

    override fun <T : Any> execute(
        requestId: String,
        type: Type,
        operation: () -> T,
    ): DeferredResult<T> {
        deferredResultStorage.find(requestId)?.let {
            return deserialize(requestId, it, type)
        }

        val ownerId = UUID.randomUUID().toString()
        val processingValue = serializer.serialize(
            result = DeferredResult<Any>(
                requestId = requestId,
                status = DeferredResultStatus.PROCESSING,
                ownerId = ownerId,
            ),
        )

        if (!deferredResultStorage.tryStartProcessing(requestId, processingValue, processingTtl)) {
            return get(requestId, type)
                ?: DeferredResult(
                    requestId = requestId,
                    status = DeferredResultStatus.PROCESSING,
                )
        }

        var heartbeat: ScheduledFuture<*>? = null
        try {
            heartbeat = lease.startHeartbeat(requestId, processingValue)
            val activeHeartbeat = heartbeat
            executor.execute {
                try {
                    if (lease.verifyOwnership(requestId, processingValue)) {
                        executeOperation(requestId, ownerId, processingValue, operation)
                    }
                } finally {
                    activeHeartbeat.cancel(false)
                }
            }
        } catch (e: Exception) {
            heartbeat?.cancel(false)
            lease.cancel(requestId, processingValue, e)
            throw e
        }

        return DeferredResult(
            requestId = requestId,
            status = DeferredResultStatus.PROCESSING,
        )
    }

    override fun <T : Any> get(
        requestId: String,
        type: Type,
    ): DeferredResult<T>? =
        deferredResultStorage.find(requestId)?.let {
            deserialize(requestId, it, type)
        }

    private fun <T : Any> deserialize(
        requestId: String,
        value: String,
        type: Type,
    ): DeferredResult<T> =
        try {
            serializer.deserialize(value, type)
        } catch (e: Exception) {
            log.error("Failed to deserialize deferred result for request {}", requestId, e)
            throw DeferredResultException(
                "Stored deferred result for request $requestId cannot be deserialized as ${type.typeName}",
                e,
            )
        }

    private fun <T : Any> executeOperation(
        requestId: String,
        ownerId: String,
        processingValue: String,
        operation: () -> T,
    ) {
        val result =
            try {
                DeferredResult(
                    requestId = requestId,
                    status = DeferredResultStatus.COMPLETED,
                    result = operation(),
                    ownerId = ownerId,
                )
            } catch (e: Exception) {
                log.error("Deferred operation failed for request {}", requestId, e)
                DeferredResult(
                    requestId = requestId,
                    status = DeferredResultStatus.FAILED,
                    error = DeferredResultError(
                        code = e.javaClass.name,
                        message = e.message ?: "Execution failed",
                    ),
                    ownerId = ownerId,
                )
            }

        val value =
            try {
                serializer.serialize(result)
            } catch (e: Exception) {
                log.error("Failed to serialize deferred result for request {}", requestId, e)
                serializer.serialize(
                    DeferredResult<Any>(
                        requestId = requestId,
                        status = DeferredResultStatus.FAILED,
                        error = DeferredResultError(
                            code = SERIALIZATION_ERROR_CODE,
                            message = "Failed to serialize operation result",
                        ),
                        ownerId = ownerId,
                    ),
                )
            }

        publish(requestId, processingValue, value)
    }

    private fun publish(
        requestId: String,
        processingValue: String,
        value: String,
    ) {
        for (attempt in 1..publishRetry.maxAttempts) {
            try {
                val published = deferredResultStorage.completeProcessing(requestId, processingValue, value, resultTtl) ||
                    deferredResultStorage.find(requestId) == value
                if (!published) {
                    log.warn("Cannot publish deferred result for request {}: processing marker expired or was replaced", requestId)
                }
                return
            } catch (e: Exception) {
                if (attempt == publishRetry.maxAttempts) {
                    log.error("Failed to publish deferred result for request {}; keeping marker until expiry", requestId, e)
                    return
                }
                log.warn("Failed to publish deferred result for request {} (attempt {})", requestId, attempt, e)
            }
            try {
                Thread.sleep(publishRetry.delay.toMillis())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DefaultDeferredResultManager::class.java)

        private const val SERIALIZATION_ERROR_CODE = "SERIALIZATION_ERROR"
    }
}
