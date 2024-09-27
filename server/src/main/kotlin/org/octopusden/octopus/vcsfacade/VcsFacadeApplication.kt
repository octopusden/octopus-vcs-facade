package org.octopusden.octopus.vcsfacade

import org.octopusden.octopus.vcsfacade.config.BitbucketProperties
import org.octopusden.octopus.vcsfacade.config.GiteaProperties
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.Bean
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude = [ElasticsearchDataAutoConfiguration::class])
@ConfigurationPropertiesScan
@EnableScheduling
class VcsFacadeApplication {
    @Bean
    fun vcsServiceBeanDefinitionRegistryPostProcessor(environment: ConfigurableEnvironment) =
        BeanDefinitionRegistryPostProcessor { beanDefinitionRegistry ->
            val binder = Binder.get(environment)
            if (binder.bind("vcs-facade.vcs.bitbucket.enabled", String::class.java).orElse("true").toBoolean()) {
                binder.bind("vcs-facade.vcs.bitbucket", BitbucketProperties::class.java)
                    .get().instances.forEach { vcsProperties ->
                        beanDefinitionRegistry.registerBeanDefinition("BitbucketService(${vcsProperties.host})",
                            GenericBeanDefinition().apply {
                                @Suppress("UsePropertyAccessSyntax") setBeanClass(BitbucketService::class.java)
                                scope = GenericBeanDefinition.SCOPE_PROTOTYPE
                                constructorArgumentValues = ConstructorArgumentValues().apply {
                                    addIndexedArgumentValue(0, vcsProperties)
                                }
                            })
                    }
            }
            if (binder.bind("vcs-facade.vcs.gitea.enabled", String::class.java).orElse("true").toBoolean()) {
                binder.bind("vcs-facade.vcs.gitea", GiteaProperties::class.java)
                    .get().instances.forEach { vcsProperties ->
                        beanDefinitionRegistry.registerBeanDefinition("GiteaService(${vcsProperties.host})",
                            GenericBeanDefinition().apply {
                                @Suppress("UsePropertyAccessSyntax") setBeanClass(GiteaService::class.java)
                                scope = GenericBeanDefinition.SCOPE_PROTOTYPE
                                constructorArgumentValues = ConstructorArgumentValues().apply {
                                    addIndexedArgumentValue(0, vcsProperties)
                                }
                            })
                    }
            }
        }
}

fun main(args: Array<String>) {
    SpringApplication.run(VcsFacadeApplication::class.java, *args)
}
