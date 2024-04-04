package org.octopusden.octopus.vcsfacade.config

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

data class ExecutorProperties(
    val corePoolSize: Int? = null,
    val maxPoolSize: Int? = null,
    val queueCapacity: Int? = null,
    val keepAliveSeconds: Int? = null,
    val allowCoreThreadTimeout: Boolean? = null,
    val preStartAllCoreThreads: Boolean? = null
) {
    fun buildThreadPoolTaskExecutor() = ThreadPoolTaskExecutor().apply {
        this@ExecutorProperties.corePoolSize?.let { corePoolSize = it }
        this@ExecutorProperties.maxPoolSize?.let { maxPoolSize = it }
        this@ExecutorProperties.queueCapacity?.let { queueCapacity = it }
        this@ExecutorProperties.keepAliveSeconds?.let { keepAliveSeconds = it }
        this@ExecutorProperties.allowCoreThreadTimeout?.let { setAllowCoreThreadTimeOut(it) }
        this@ExecutorProperties.preStartAllCoreThreads?.let { setPrestartAllCoreThreads(it) }
    }
}
