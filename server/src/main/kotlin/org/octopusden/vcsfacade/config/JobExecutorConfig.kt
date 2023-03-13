package org.octopusden.vcsfacade.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class JobExecutorConfig(private val jobProperties: JobProperties) {
    @Bean(name = ["jobExecutor"])
    fun taskExecutor(): AsyncTaskExecutor {
        val jobExecutorProperties = jobProperties.executor
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = jobExecutorProperties.corePoolSize
        executor.maxPoolSize = jobExecutorProperties.maxPoolSize
        executor.setQueueCapacity(jobExecutorProperties.queueCapacity)
        return executor
    }
}
