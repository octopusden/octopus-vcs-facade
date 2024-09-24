package org.octopusden.octopus.vcsfacade.config

import java.net.InetAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${vcs-facade.master}") val master: String
) : WebMvcConfigurer, InfoContributor {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/", "swagger-ui/index.html")
    }

    @Bean
    fun isMaster() = master.isBlank() || master.equals(InetAddress.getLocalHost().hostName, true)

    override fun contribute(builder: Info.Builder) {
        builder.withDetail("isMaster", isMaster())
    }
}
