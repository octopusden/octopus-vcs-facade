package org.octopusden.octopus.vcsfacade.deferred.result.spring

import org.octopusden.octopus.vcsfacade.deferred.result.core.model.PublishRetryPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("deferred-result")
data class DeferredResultProperties(
    val enabled: Boolean = false,
    val namespace: String = "deferred-result",
    val processingTtl: Duration = Duration.ofMinutes(15),
    val resultTtl: Duration = Duration.ofMinutes(5),
    val publishRetry: PublishRetryPolicy = PublishRetryPolicy(),
    val valkey: Valkey = Valkey(),
    val executor: Executor = Executor(),
) {
    init {
        require(processingTtl.toMillis() > 0) {
            "deferred-result.processing-ttl ($processingTtl) must be at least one millisecond"
        }
        require(resultTtl.toMillis() > 0) {
            "deferred-result.result-ttl ($resultTtl) must be at least one millisecond"
        }
    }

    data class Valkey(
        val host: String = "localhost",
        val port: Int = 6379,
        val username: String? = null,
        val password: String? = null,
        val database: Int = 0,
        val connectionPoolSize: Int = 16,
        val connectionMinimumIdleSize: Int = 4,
    )

    data class Executor(
        val corePoolSize: Int = 4,
        val maxPoolSize: Int = 8,
        val queueCapacity: Int = 100,
        val keepAliveSeconds: Int? = null,
        val allowCoreThreadTimeout: Boolean? = null,
        val preStartAllCoreThreads: Boolean? = null,
    )
}
