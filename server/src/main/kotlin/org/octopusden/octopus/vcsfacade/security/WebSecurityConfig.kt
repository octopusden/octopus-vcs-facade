package org.octopusden.octopus.vcsfacade.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.cloud.commons.security.config.AuthServerProperties
import org.octopusden.cloud.commons.security.config.CloudCommonWebSecurityConfig
import org.octopusden.cloud.commons.security.config.SecurityProperties
import org.octopusden.cloud.commons.security.converter.UserInfoGrantedAuthoritiesConverter
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * Security of the repository provisioning endpoints, loaded only when provisioning is enabled.
 *
 * The library's own chain authenticates every request, which would lock the existing, public endpoints.
 * This one authenticates the three provisioning calls (method and exact path) and leaves the rest open.
 * What a caller may do is decided on the method, by `@PreAuthorize` on the controller.
 *
 * `AuthServerClient` reads the auth server's OpenID configuration when the context starts, so the auth
 * server must be reachable then.
 */
@Configuration
@ConditionalOnProvisioningEnabled
@Import(AuthServerClient::class)
class WebSecurityConfig(
    private val authServerClient: AuthServerClient,
    private val authServerProperties: AuthServerProperties,
    private val objectMapper: ObjectMapper,
    securityProperties: SecurityProperties,
) : CloudCommonWebSecurityConfig(authServerClient, securityProperties) {
    @Bean
    override fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/rest/api/2/groups", "/rest/api/2/repository/state")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/rest/api/2/repository")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { oauth2 ->
                oauth2
                    .jwt { jwt ->
                        jwt.jwtAuthenticationConverter(
                            JwtAuthenticationConverter().apply {
                                setJwtGrantedAuthoritiesConverter(UserInfoGrantedAuthoritiesConverter(authServerClient))
                            },
                        )
                    }
                    // A rejected token answers through these too, not through the resource server's empty 401.
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                    .accessDeniedHandler(jsonAccessDeniedHandler())
            }.exceptionHandling { ex ->
                ex.authenticationEntryPoint(jsonAuthenticationEntryPoint())
                ex.accessDeniedHandler(jsonAccessDeniedHandler())
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Stateless resource server: every protected call carries a bearer token in a header, which a
            // cross-site form post cannot set, and the existing POST and DELETE endpoints take no token at all.
            .csrf { it.disable() }
            .cors { it.disable() }
            .build()

    @Bean
    fun jsonAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, ex: AuthenticationException ->
            response.writeError(objectMapper, HttpServletResponse.SC_UNAUTHORIZED, ex.localizedMessage.orEmpty().ifBlank { "Unauthorized" })
        }

    @Bean
    fun jsonAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { _: HttpServletRequest, response: HttpServletResponse, ex: AccessDeniedException ->
            response.writeError(objectMapper, HttpServletResponse.SC_FORBIDDEN, ex.localizedMessage.orEmpty().ifBlank { "Forbidden" })
        }

    /**
     * Signature, expiry and issuer are checked; the issuer comes from `auth-server.url` and `auth-server.realm`.
     * The key set is fetched on first use, not at startup.
     */
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val url = authServerProperties.url
        val realm = authServerProperties.realm
        if (url.isNullOrBlank() || realm.isNullOrBlank()) {
            throw BeanInitializationException("auth-server.url and auth-server.realm must both be set (got url='$url', realm='$realm')")
        }
        val issuer = "${url.trimEnd('/')}/realms/$realm"
        return NimbusJwtDecoder.withJwkSetUri("$issuer/protocol/openid-connect/certs").build().apply {
            setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefault(), JwtIssuerValidator(issuer)))
        }
    }
}
