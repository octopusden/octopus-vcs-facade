package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.config.JobConfig
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Component
import java.util.Date
import java.lang.reflect.Type
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(prefix = "deferred-result", name = ["enabled"], havingValue = "false", matchIfMissing = true)
@Deprecated(
    message = "Use DeferredRequestProcessor (deferred-result.enabled=true) instead",
)
class LocalRequestProcessor(
    private val jobProperties: JobConfig.JobProperties,
    @Qualifier("jobExecutor") private val jobExecutor: AsyncTaskExecutor,
) : RequestProcessor {
    private val requestJobs = ConcurrentHashMap<String, Future<*>>()

    override fun <T : Any> process(
        requestId: String,
        type: Type,
        func: () -> T,
    ): T {
        with(
            requestJobs.computeIfAbsent(requestId) { newRequest ->
                log.debug("Submit request {}", newRequest)
                val future =
                    jobExecutor.submit(
                        Callable {
                            log.trace("Start executing request {}", newRequest)
                            val result = func()
                            log.trace("Finish executing request {}", newRequest)
                            result
                        },
                    )
                val waitThreshold = Date(Date().time + (jobProperties.fastWorkTimoutSecs * 1000))
                while (Date().before(waitThreshold) && !future.isDone) {
                    TimeUnit.MILLISECONDS.sleep(200)
                }
                future
            },
        ) {
            if (!isDone) {
                throw JobProcessingException(
                    "Request $requestId is still processing",
                    requestId,
                    Date(Date().time + (jobProperties.retryIntervalSecs * 1000)),
                )
            }
        }
        log.debug("Collect request {} result", requestId)
        try {
            @Suppress("UNCHECKED_CAST")
            return requestJobs.remove(requestId)!!.get() as T
        } catch (e: ExecutionException) {
            throw e.cause!!
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalRequestProcessor::class.java)
    }
}
