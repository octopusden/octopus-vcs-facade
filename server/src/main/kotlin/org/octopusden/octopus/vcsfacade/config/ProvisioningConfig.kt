package org.octopusden.octopus.vcsfacade.config

import jakarta.servlet.MultipartConfigElement
import org.octopusden.octopus.vcsfacade.security.ConditionalOnProvisioningEnabled
import org.octopusden.octopus.vcsfacade.service.ContentValidator
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.multipart.support.StandardServletMultipartResolver

/**
 * Upload handling of the provisioning endpoint, only while provisioning is on; with it off, Spring Boot's
 * `spring.servlet.multipart.*` settings and its resolver stay as they were.
 */
@Configuration
@ConditionalOnProvisioningEnabled
class ProvisioningConfig {
    /**
     * The upload limits follow `vcs-facade.provisioning.max-content-size`: the file part may be that large, the
     * whole request that plus room for the other parts and the multipart framing. This replaces Spring Boot's
     * `spring.servlet.multipart.*` limits.
     */
    @Bean
    fun multipartConfigElement(properties: ProvisioningProperties): MultipartConfigElement =
        MultipartConfigFactory()
            .apply {
                setMaxFileSize(properties.maxContentSize)
                setMaxRequestSize(DataSize.ofBytes(properties.maxContentSize.toBytes() + REQUEST_OVERHEAD_BYTES))
            }.createMultipartConfig()

    /**
     * Parses the upload when a handler argument asks for it, not in the `DispatcherServlet` before a handler is chosen.
     * An upload over the limit then fails inside the handler call, where `ProvisioningExceptionHandler` answers 413;
     * parsed up front it would fail before any handler is known, and the catch-all would answer 500.
     * Spring Boot's own resolver backs off when this bean exists; it is the same class, only lazy.
     */
    @Bean
    fun multipartResolver(): MultipartResolver = StandardServletMultipartResolver().apply { setResolveLazily(true) }

    @Bean
    fun contentValidator(properties: ProvisioningProperties): ContentValidator = ContentValidator(properties.maxContentSize.toBytes())

    companion object {
        private const val REQUEST_OVERHEAD_BYTES = 64L * 1024
    }
}
