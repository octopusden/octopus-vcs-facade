package org.octopusden.octopus.vcsfacade.deferred.result.core

import org.octopusden.octopus.vcsfacade.deferred.result.storage.DeferredResultStorage
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProcessingLease(
    private val storage: DeferredResultStorage,
    private val scheduler: ScheduledExecutorService,
    private val ttl: Duration,
    private val cancelRetryInterval: Duration,
) {
    fun startHeartbeat(
        requestId: String,
        processingValue: String,
    ): ScheduledFuture<*> {
        val interval = interval()
        return scheduler.scheduleWithFixedDelay({
            val owned =
                try {
                    storage.renewProcessing(requestId, processingValue, ttl)
                } catch (e: Exception) {
                    log.warn("Failed to renew processing marker for request {}", requestId, e)
                    return@scheduleWithFixedDelay
                }
            if (!owned) {
                throw CancellationException("Processing marker replaced or expired")
            }
        }, interval, interval, TimeUnit.MILLISECONDS)
    }

    fun verifyOwnership(
        requestId: String,
        processingValue: String,
    ): Boolean =
        try {
            storage.renewProcessing(requestId, processingValue, ttl).also { owned ->
                if (!owned) log.warn("Skipping request {} after losing its processing marker", requestId)
            }
        } catch (e: Exception) {
            log.error("Skipping request {} because ownership could not be verified; cancelling marker", requestId, e)
            cancel(requestId, processingValue, e)
            false
        }

    fun cancel(
        requestId: String,
        processingValue: String,
        cause: Exception? = null,
    ) {
        try {
            storage.cancelProcessing(requestId, processingValue)
        } catch (e: Exception) {
            cause?.addSuppressed(e)
            log.error("Failed to cancel processing marker for request {}; scheduling retry", requestId, e)
            scheduleCancelRetry(requestId, processingValue)
        }
    }

    private fun scheduleCancelRetry(
        requestId: String,
        processingValue: String,
    ) {
        val interval = cancelRetryInterval.toMillis().coerceAtLeast(1)
        val attempts = ((ttl.toMillis() + interval - 1) / interval).toInt().coerceAtLeast(1)
        val remaining = AtomicInteger(attempts)
        lateinit var future: ScheduledFuture<*>
        try {
            future =
                scheduler.scheduleWithFixedDelay({
                    try {
                        storage.cancelProcessing(requestId, processingValue)
                        future.cancel(false)
                    } catch (e: Exception) {
                        if (remaining.decrementAndGet() <= 0) {
                            log.error("Giving up cancelling processing marker for request {}; it will expire", requestId, e)
                            future.cancel(false)
                        } else {
                            log.warn("Failed to cancel processing marker for request {}; will retry", requestId, e)
                        }
                    }
                }, interval, interval, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            log.warn("Cannot schedule marker cancellation for request {}; it will expire", requestId, e)
        }
    }

    private fun interval(): Long = (ttl.toMillis() / RENEWAL_INTERVAL_DIVISOR).coerceAtLeast(1)

    companion object {
        private const val RENEWAL_INTERVAL_DIVISOR = 3L
        private val log = LoggerFactory.getLogger(ProcessingLease::class.java)
    }
}
