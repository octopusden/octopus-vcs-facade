package org.octopusden.octopus.vcsfacade.deferred.result.core.model

import java.time.Duration

data class PublishRetryPolicy(
    val maxAttempts: Int = 5,
    val delay: Duration = Duration.ofMillis(1500),
) {
    init {
        require(maxAttempts >= 1) {
            "publish-retry.max-attempts ($maxAttempts) must be at least 1"
        }
        require(!delay.isNegative) {
            "publish-retry.delay ($delay) must not be negative"
        }
    }
}
