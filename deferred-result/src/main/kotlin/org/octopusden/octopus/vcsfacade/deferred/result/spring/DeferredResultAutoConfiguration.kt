package org.octopusden.octopus.vcsfacade.deferred.result.spring

import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.vcsfacade.deferred.result.core.DefaultDeferredResultManager
import org.octopusden.octopus.vcsfacade.deferred.result.core.DeferredResultManager
import org.octopusden.octopus.vcsfacade.deferred.result.serialize.DeferredResultSerializer
import org.octopusden.octopus.vcsfacade.deferred.result.serialize.JacksonDeferredResultSerializer
import org.octopusden.octopus.vcsfacade.deferred.result.storage.DeferredResultStorage
import org.octopusden.octopus.vcsfacade.deferred.result.storage.ValkeyDeferredResultStorage
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

@AutoConfiguration
@EnableConfigurationProperties(DeferredResultProperties::class)
@ConditionalOnProperty(
    prefix = "deferred-result",
    name = ["enabled"],
    havingValue = "true",
)
class DeferredResultAutoConfiguration {
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    fun redissonClient(properties: DeferredResultProperties): RedissonClient =
        Redisson.create(
            ValkeyConfigFactory.create(properties.valkey),
        )

    @Bean
    @ConditionalOnMissingBean
    fun deferredResultStorage(
        redissonClient: RedissonClient,
        properties: DeferredResultProperties,
    ): DeferredResultStorage =
        ValkeyDeferredResultStorage(
            redissonClient = redissonClient,
            namespace = properties.namespace,
        )

    @Bean
    @ConditionalOnMissingBean
    fun deferredResultSerializer(objectMapper: ObjectMapper): DeferredResultSerializer = JacksonDeferredResultSerializer(objectMapper)

    @Bean(name = [DEFERRED_RESULT_EXECUTOR])
    @ConditionalOnMissingBean(name = [DEFERRED_RESULT_EXECUTOR])
    fun deferredResultExecutor(properties: DeferredResultProperties): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            val executor = properties.executor

            executor.corePoolSize.let { corePoolSize = it }
            executor.maxPoolSize.let { maxPoolSize = it }
            executor.queueCapacity.let { queueCapacity = it }
            executor.keepAliveSeconds?.let { keepAliveSeconds = it }
            executor.allowCoreThreadTimeout?.let { setAllowCoreThreadTimeOut(it) }
            executor.preStartAllCoreThreads?.let { setPrestartAllCoreThreads(it) }

            setThreadNamePrefix(DEFERRED_RESULT_THREAD_NAME_PREFIX)
        }

    @Bean(name = [RENEWAL_SCHEDULER], destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = [RENEWAL_SCHEDULER])
    fun deferredResultHeartbeatScheduler(): ScheduledExecutorService =
        ScheduledThreadPoolExecutor(1) { task -> Thread(task, "deferred-result-heartbeat") }.apply {
            removeOnCancelPolicy = true
        }

    @Bean
    @ConditionalOnMissingBean
    fun deferredResultManager(
        deferredResultStorage: DeferredResultStorage,
        deferredResultSerializer: DeferredResultSerializer,
        @Qualifier(DEFERRED_RESULT_EXECUTOR)
        executor: Executor,
        @Qualifier(RENEWAL_SCHEDULER) renewalScheduler: ScheduledExecutorService,
        properties: DeferredResultProperties,
    ): DeferredResultManager =
        DefaultDeferredResultManager(
            deferredResultStorage = deferredResultStorage,
            serializer = deferredResultSerializer,
            executor = executor,
            renewalScheduler = renewalScheduler,
            processingTtl = properties.processingTtl,
            resultTtl = properties.resultTtl,
            publishRetry = properties.publishRetry,
        )

    companion object {
        const val RENEWAL_SCHEDULER = "deferredResultHeartbeatScheduler"
        const val DEFERRED_RESULT_EXECUTOR = "deferredResultExecutor"
        const val DEFERRED_RESULT_THREAD_NAME_PREFIX = "deferred-result-"
    }
}
