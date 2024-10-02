package org.octopusden.octopus.vcsfacade.config

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment

@Configuration
class VcsConfig {
    data class VcsServiceHealthCheckProperties(
        val repo: String,
        val rootCommit: String,
        val lastRelease: String,
        val expectedCommits: Set<String>
    )

    data class VcsServiceProperties(
        val type: VcsServiceType,
        val host: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: VcsServiceHealthCheckProperties?
    ) {
        fun getCredentialProvider() = if (token != null) {
            if (type == VcsServiceType.BITBUCKET) {
                BitbucketBearerTokenCredentialProvider(token)
            } else {
                StandardBearerTokenCredentialProvider(token)
            }
        } else if (username != null && password != null) {
            if (type == VcsServiceType.BITBUCKET) {
                BitbucketBasicCredentialProvider(username, password)
            } else {
                StandardBasicCredCredentialProvider(username, password)
            }
        } else {
            throw IllegalStateException("Auth token or username/password must be specified")
        }
    }

    @ConfigurationProperties("vcs-facade.vcs")
    data class VcsProperties(
        val services: List<VcsServiceProperties>
    )

    @Bean
    fun vcsServiceBeanDefinitionRegistryPostProcessor(environment: ConfigurableEnvironment) =
        BeanDefinitionRegistryPostProcessor { beanDefinitionRegistry ->
            Binder.get(environment).bind("vcs-facade.vcs", VcsProperties::class.java).get().services.forEach {
                val serviceClass = when (it.type) {
                    VcsServiceType.BITBUCKET -> BitbucketService::class.java
                    VcsServiceType.GITEA -> GiteaService::class.java
                }
                beanDefinitionRegistry.registerBeanDefinition("${serviceClass.simpleName}(${it.host})",
                    GenericBeanDefinition().apply {
                        @Suppress("UsePropertyAccessSyntax") setBeanClass(serviceClass)
                        scope = GenericBeanDefinition.SCOPE_PROTOTYPE
                        constructorArgumentValues = ConstructorArgumentValues().apply {
                            addIndexedArgumentValue(0, it)
                        }
                    })
            }
        }
}