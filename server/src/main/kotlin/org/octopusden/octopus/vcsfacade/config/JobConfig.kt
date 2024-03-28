package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class JobConfig(private val jobProperties: JobProperties) {
    @ConfigurationProperties("vcs-facade.job")
    data class JobProperties(val fastWorkTimoutSecs: Int, val retryIntervalSecs: Int, val executor: ExecutorProperties)

    @Bean
    fun jobExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = jobProperties.executor.corePoolSize
        maxPoolSize = jobProperties.executor.maxPoolSize
        queueCapacity = jobProperties.executor.queueCapacity
    }
}
