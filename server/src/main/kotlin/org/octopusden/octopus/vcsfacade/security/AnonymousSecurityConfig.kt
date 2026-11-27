package org.octopusden.octopus.vcsfacade.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * The default: provisioning is off, so nothing is authenticated. Spring Security is on the classpath, and
 * without a chain of its own Spring Boot would put basic authentication on every endpoint.
 * This chain matches no request, so every request passes without any security filter.
 */
@Configuration
@ConditionalOnProperty(name = ["vcs-facade.provisioning.enabled"], havingValue = "false", matchIfMissing = true)
class AnonymousSecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http.securityMatcher(RequestMatcher { false }).build()
}
