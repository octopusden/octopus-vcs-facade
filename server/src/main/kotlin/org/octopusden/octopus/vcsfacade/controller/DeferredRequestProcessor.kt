package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.config.JobConfig
import org.octopusden.octopus.vcsfacade.deferred.result.core.DeferredResultManager
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResult
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.lang.reflect.Type
import java.util.Date
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(prefix = "deferred-result", name = ["enabled"], havingValue = "true")
class DeferredRequestProcessor(
    private val jobProperties: JobConfig.JobProperties,
    private val deferredResultManager: DeferredResultManager,
) : RequestProcessor {
    override fun <T : Any> process(
        requestId: String,
        type: Type,
        func: () -> T,
    ): T {
        val deferred = deferredResultManager.execute(requestId, type) { func() }
        return resolve(requestId, type, deferred)
    }

    private fun <T : Any> resolve(
        requestId: String,
        type: Type,
        deferred: DeferredResult<T>,
    ): T {
        var result = deferred
        val waitUntil = System.currentTimeMillis() + jobProperties.fastWorkTimoutSecs * 1000L
        while (result.status == DeferredResultStatus.PROCESSING && System.currentTimeMillis() < waitUntil) {
            TimeUnit.MILLISECONDS.sleep(200)
            result = deferredResultManager.get(requestId, type) ?: result
        }
        return when (result.status) {
            DeferredResultStatus.COMPLETED -> result.result!!
            DeferredResultStatus.FAILED -> throw recreateException(result)
            DeferredResultStatus.PROCESSING ->
                throw JobProcessingException(
                    "Request $requestId is still processing",
                    requestId,
                    Date(Date().time + (jobProperties.retryIntervalSecs * 1000)),
                )
        }
    }

    private fun <T : Any> recreateException(result: DeferredResult<T>): Exception {
        val error = result.error ?: return RuntimeException("Deferred result failed")
        return try {
            val clazz = Class.forName(error.code)
            clazz.getConstructor(String::class.java).newInstance(error.message) as Exception
        } catch (_: Exception) {
            RuntimeException(error.message)
        }
    }
}
