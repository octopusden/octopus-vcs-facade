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
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthenticatedAuthorizationManager
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationManagers
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
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher

/**
 * Security of the repository provisioning endpoints, loaded only when provisioning is enabled.
 *
 * The library's own chain authenticates every request, which would lock the existing, public endpoints.
 * This one matches only the provisioning calls (method and exact path) and authenticates them. Every other
 * request matches no chain, so it passes no security filter and gets no Spring Security headers.
 * CSRF protection stays on and never checks the reads. The create is authenticated by a bearer token alone,
 * so only that one POST is exempted from it.
 * What a caller may do is decided on the method, by `@PreAuthorize` on the controller. The create also asks for
 * its permission here, in the chain, so a caller without it is answered before the upload is parsed.
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
    private val permissionEvaluator: ObjectProvider<ProvisioningPermissionEvaluator>,
) : CloudCommonWebSecurityConfig(authServerClient, securityProperties) {
    @Bean
    override fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(
                // HEAD too: Spring MVC serves it from the GET handlers.
                OrRequestMatcher(
                    PROVISIONING_PATHS.flatMap { path ->
                        listOf(HttpMethod.GET, HttpMethod.HEAD).map { AntPathRequestMatcher.antMatcher(it, path) }
                    } + CREATE_MATCHER,
                ),
            ).csrf { it.ignoringRequestMatchers(CREATE_MATCHER) }
            .authorizeHttpRequests {
                // The create needs the permission here, in the chain, so it is settled before its upload is parsed.
                it
                    .requestMatchers(CREATE_MATCHER)
                    .access(createManager())
                    .anyRequest()
                    .authenticated()
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
            .build()

    /**
     * Authenticated, then the same permission `@PreAuthorize` asks for, through the same evaluator. The first check
     * comes first so a caller without a token is answered 401, not 403.
     */
    private fun createManager(): AuthorizationManager<RequestAuthorizationContext> =
        AuthorizationManagers.allOf(
            AuthenticatedAuthorizationManager.authenticated<RequestAuthorizationContext>(),
            AuthorizationManager<RequestAuthorizationContext> { _, _ ->
                AuthorizationDecision(permissionEvaluator.getObject().hasPermission(CREATE_PERMISSION))
            },
        )

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

    private companion object {
        val PROVISIONING_PATHS = listOf("/rest/api/2/groups", "/rest/api/2/repository/state")
        const val CREATE_PERMISSION = "CREATE_REPOSITORIES"
        val CREATE_MATCHER = AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/rest/api/2/repository")
    }
}
