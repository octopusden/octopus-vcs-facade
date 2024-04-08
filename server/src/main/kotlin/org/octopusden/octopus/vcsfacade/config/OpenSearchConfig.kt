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
class OpenSearchConfig(val openSearchProperties: OpenSearchProperties?) {
    data class OpenSearchIndexProperties(val suffix: String)

    @ConfigurationProperties("opensearch")
    @ConditionalOnProperty(
        prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    data class OpenSearchProperties(
        val host: String,
        val ssl: Boolean = true,
        val username: String,
        val password: String,
        val index: OpenSearchIndexProperties
    )

    @Bean //dedicated bean to simplify SpEL expression
    fun opensearchIndexSuffix() = openSearchProperties?.index?.suffix

    @Configuration
    @ConditionalOnProperty(
        prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    class OpenSearchClient(
        val openSearchProperties: OpenSearchProperties
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
    }
}
