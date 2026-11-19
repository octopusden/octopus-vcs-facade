package org.octopusden.octopus.vcsfacade.deferred.result.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.deferred.result.core.DeferredResultManager
import org.octopusden.octopus.vcsfacade.deferred.result.storage.DeferredResultStorage
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.lang.reflect.Proxy
import java.util.function.Supplier

class DeferredResultAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DeferredResultAutoConfiguration::class.java))
        .withBean(ObjectMapper::class.java, { jacksonObjectMapper() })
        .withBean(RedissonClient::class.java, { redissonStub() })
        .withPropertyValues("deferred-result.enabled=true")

    @Test
    fun createsDeferredResultBeansWhenEnabled() {
        runner.run { context ->
            assertThat(context).hasSingleBean(DeferredResultManager::class.java)
            assertThat(context).hasSingleBean(DeferredResultStorage::class.java)
            assertThat(context).hasBean(DeferredResultAutoConfiguration.DEFERRED_RESULT_EXECUTOR)
            assertThat(context).hasBean(DeferredResultAutoConfiguration.RENEWAL_SCHEDULER)
        }
    }

    @Test
    fun doesNotCreateBeansWhenDisabled() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DeferredResultAutoConfiguration::class.java))
            .withBean(ObjectMapper::class.java, Supplier { jacksonObjectMapper() })
            .withPropertyValues("deferred-result.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(DeferredResultManager::class.java)
                assertThat(context).doesNotHaveBean(DeferredResultStorage::class.java)
            }
    }

    @Test
    fun bindsValkeyConnectionProperties() {
        val contextRunner =
            runner.withPropertyValues(
                "deferred-result.valkey.host=valkey.example.com",
                "deferred-result.valkey.port=6380",
                "deferred-result.valkey.database=2",
            )
        contextRunner.run { context ->
            val properties = context.getBean(DeferredResultProperties::class.java)
            assertThat(properties.valkey.host).isEqualTo("valkey.example.com")
            assertThat(properties.valkey.port).isEqualTo(6380)
            assertThat(properties.valkey.database).isEqualTo(2)
        }
    }

    @Test
    fun bindsIndependentTtls() {
        runner.withPropertyValues("deferred-result.processing-ttl=20m", "deferred-result.result-ttl=5m").run { context ->
            val properties = context.getBean(DeferredResultProperties::class.java)
            assertThat(properties.processingTtl).isEqualTo(java.time.Duration.ofMinutes(20))
            assertThat(properties.resultTtl).isEqualTo(java.time.Duration.ofMinutes(5))
        }
    }

    @Test
    fun rejectsNonPositiveTtls() {
        listOf("processing-ttl", "result-ttl").forEach { property ->
            runner.withPropertyValues("deferred-result.$property=0ms").run { context ->
                assertThat(context).hasFailed()
            }
        }
    }

    private fun redissonStub(): RedissonClient =
        Proxy.newProxyInstance(
            RedissonClient::class.java.classLoader,
            arrayOf(RedissonClient::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "RedissonClientStub"
                "hashCode" -> 0
                "equals" -> false
                else -> throw UnsupportedOperationException(method.name)
            }
        } as RedissonClient
}
