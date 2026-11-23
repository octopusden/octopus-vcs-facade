package org.octopusden.octopus.vcsfacade.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * The default: provisioning is off, so nothing is authenticated. Spring Security is on the classpath, and
 * without a chain of its own Spring Boot would put basic authentication on every endpoint.
 */
@Configuration
@ConditionalOnProperty(name = ["vcs-facade.provisioning.enabled"], havingValue = "false", matchIfMissing = true)
class AnonymousSecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            // No Spring Security response headers either: every response stays as it was before security was added.
            .headers { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Stateless service without cookies: there is no session a cross-site request could ride, and the
            // existing POST and DELETE endpoints (tags, pull requests, webhooks) are called without a token.
            .csrf { it.disable() }
            .cors { it.disable() }
            .build()
}
