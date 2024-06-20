package org.octopusden.octopus.vcsfacade.client

import feign.RetryableException
import feign.Retryer
import java.util.Date
import java.util.concurrent.TimeUnit
import org.apache.http.HttpStatus
import org.slf4j.LoggerFactory

class VcsFacadeRetryer(private val timeRetryInMillis: Int = 60000) : Retryer {
    private val timeDelayIteration: Int = timeRetryInMillis / NUMBER_ITERATIONS - (NUMBER_ATTEMPTS * TIME_DELAY_ATTEMPT)
    private val stopTime = System.currentTimeMillis() + timeRetryInMillis
    private var attempt: Int = NUMBER_ATTEMPTS
    private var iteration: Int = NUMBER_ITERATIONS

    override fun continueOrPropagate(e: RetryableException) {
        strategies.getOrDefault(e.status()) {
            if (stopTime < System.currentTimeMillis()) {
                throw e.cause!!
            }
            if (log.isDebugEnabled) {
                log.debug("Retry: iteration=${NUMBER_ITERATIONS - iteration + 1}, attempt=${NUMBER_ATTEMPTS - attempt + 1}")
            }
            if (attempt-- > 0) {
                TimeUnit.MILLISECONDS.sleep(TIME_DELAY_ATTEMPT.toLong())
            } else if (iteration-- > 0) {
                attempt = NUMBER_ATTEMPTS
                TimeUnit.MILLISECONDS.sleep(timeDelayIteration.toLong())
            } else {
                throw e.cause!!
            }
        }.invoke(e)
    }

    override fun clone(): Retryer {
        return VcsFacadeRetryer(timeRetryInMillis)
    }

    companion object {
        private const val NUMBER_ATTEMPTS = 5
        private const val TIME_DELAY_ATTEMPT = 300
        private const val NUMBER_ITERATIONS = 5

        private val log = LoggerFactory.getLogger(VcsFacadeRetryer::class.java)
        private val strategies = mapOf<Int, (e: RetryableException) -> Unit>(HttpStatus.SC_ACCEPTED to { e ->
            val currentDate = Date()
            val retryAfterDate = Date(e.retryAfter())
            log.debug("Deferred result retry after {}", retryAfterDate)
            if (retryAfterDate.after(currentDate)) {
                val sleepingTime = retryAfterDate.time - currentDate.time
                log.trace("Sleeping time {}ms", sleepingTime)
                Thread.sleep(sleepingTime)
            }
        })
    }
}
