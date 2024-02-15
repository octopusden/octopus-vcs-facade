package org.octopusden.octopus.vcsfacade.client

import feign.RetryableException
import feign.Retryer
import org.apache.http.HttpStatus
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.concurrent.TimeUnit

const val numberAttempts: Int = 5
const val timeDelayAttempt: Int = 300
const val numberIterations: Int = 5

class VcsFacadeRetry(private val timeRetryInMillis: Int = 60000) : Retryer {
    private val timeDelayIteration: Int = timeRetryInMillis / numberIterations - (numberAttempts * timeDelayAttempt)
    private val stopTime = System.currentTimeMillis() + timeRetryInMillis

    private var attempt: Int = numberAttempts
    private var iteration: Int = numberIterations

    override fun continueOrPropagate(e: RetryableException) {

        strategies.getOrDefault(e.status()) {
            if (stopTime < System.currentTimeMillis()) {
                throw e.cause!!
            }

            log.debug("Retry: iteration=${numberIterations - iteration + 1}, attempt=${numberAttempts - attempt + 1}")

            if (attempt-- > 0) {
                TimeUnit.MILLISECONDS.sleep(timeDelayAttempt.toLong())
            } else if (iteration-- > 0) {
                attempt = numberAttempts

                TimeUnit.MILLISECONDS.sleep(timeDelayIteration.toLong())
            } else {
                throw e.cause!!
            }

        }
            .invoke(e)
    }

    override fun clone(): Retryer {
        return VcsFacadeRetry(timeRetryInMillis)
    }

    companion object {
        private val log = LoggerFactory.getLogger(VcsFacadeRetry::class.java)
        private val strategies = mapOf<Int, (e: RetryableException) -> Unit>(
            HttpStatus.SC_ACCEPTED to { e ->
                val currentDate = Date()
                val retryAfterDate = Date(e.retryAfter())

                log.debug("Deferred result retry after $retryAfterDate")

                if (retryAfterDate.after(currentDate)) {
                    val sleepingTime = retryAfterDate.time - currentDate.time
                    log.trace("Sleeping time ${sleepingTime}ms")
                    Thread.sleep(sleepingTime)
                }
            }
        )
    }
}
