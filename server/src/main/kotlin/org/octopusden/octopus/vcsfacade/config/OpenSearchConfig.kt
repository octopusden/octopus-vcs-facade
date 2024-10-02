package org.octopusden.octopus.vcsfacade.config

import org.opensearch.client.RestHighLevelClient
import org.opensearch.data.client.orhlc.AbstractOpenSearchConfiguration
import org.opensearch.data.client.orhlc.ClientConfiguration
import org.opensearch.data.client.orhlc.RestClients
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class OpenSearchConfig(private val openSearchProperties: OpenSearchProperties?) {
    data class OpenSearchIndexScanProperties(
        val cron: String?,
        val delay: Long?,
        val executor: ExecutorProperties?
    )

    data class OpenSearchIndexProperties(
        val suffix: String,
        val webhookSecret: String?,
        val scan: OpenSearchIndexScanProperties?
    )

    @ConfigurationProperties("vcs-facade.opensearch")
    @ConditionalOnProperty(
        prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    data class OpenSearchProperties(
        val host: String,
        val ssl: Boolean = true,
        val username: String,
        val password: String,
        val index: OpenSearchIndexProperties
    )

    @Configuration
    @ConditionalOnProperty(
        prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    class OpenSearchEnabledConfig(
        private val openSearchProperties: OpenSearchProperties
    ) : AbstractOpenSearchConfiguration() {
        override fun opensearchClient(): RestHighLevelClient {
            val clientConfigurationBuilder = if (openSearchProperties.ssl) {
                ClientConfiguration.builder().connectedTo(openSearchProperties.host).usingSsl()
            } else {
                ClientConfiguration.builder().connectedTo(openSearchProperties.host)
            }
            return RestClients.create(
                clientConfigurationBuilder.withBasicAuth(
                    openSearchProperties.username, openSearchProperties.password
                ).build()
            ).rest()
        }

        @Bean
        fun opensearchIndexScanExecutor() =
            (openSearchProperties.index.scan?.executor ?: ExecutorProperties()).buildThreadPoolTaskExecutor()
    }

    @Bean //dedicated bean to simplify SpEL expression
    fun opensearchIndexSuffix() = openSearchProperties?.index?.suffix ?: "undefined"

    @Bean //dedicated bean to simplify SpEL expression
    fun opensearchIndexScheduleRepositoriesRescanCron() = openSearchProperties?.index?.scan?.cron ?: "-"

    @Bean //dedicated bean to simplify SpEL expression
    fun opensearchIndexSubmitScheduledRepositoriesScanFixedDelay() = openSearchProperties?.index?.scan?.delay ?: 60000
}
