package org.octopusden.octopus.vcsfacade.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.security.ConditionalOnProvisioningEnabled
import org.octopusden.octopus.vcsfacade.service.ProvisioningTargetResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Settles the target of the create before its arguments are resolved, which is when the upload is parsed.
 */
@Configuration
@ConditionalOnProvisioningEnabled
class ProvisioningWebConfig(
    private val targetResolver: ProvisioningTargetResolver,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(CreateTargetInterceptor(targetResolver)).addPathPatterns(CREATE_PATH)
    }

    /**
     * Resolves `sshUrl` against the allow-list and hands the target to the controller as the request attribute
     * [TARGET_ATTRIBUTE]. Runs after the security chain (token, permission) and before the argument resolvers, so a
     * target outside the allow-list answers 403 however large the upload is.
     *
     * The `sshUrl` is read from the query string only: `getParameter` would parse the multipart body, and an
     * oversized one would fail there, ahead of this check.
     */
    class CreateTargetInterceptor(
        private val targetResolver: ProvisioningTargetResolver,
    ) : HandlerInterceptor {
        override fun preHandle(
            request: HttpServletRequest,
            response: HttpServletResponse,
            handler: Any,
        ): Boolean {
            if (request.method == "POST") {
                val sshUrl = queryParameter(request.queryString, "sshUrl") ?: throw InvalidRequestException("The sshUrl must be given")
                request.setAttribute(TARGET_ATTRIBUTE, targetResolver.resolve(sshUrl))
            }
            return true
        }

        private fun queryParameter(
            query: String?,
            name: String,
        ): String? =
            query
                ?.split('&')
                ?.map { it.substringBefore('=') to it.substringAfter('=', "") }
                ?.firstOrNull { (key, _) -> decode(key) == name }
                ?.let { (_, value) -> decode(value) }

        // A malformed escape is the caller's mistake: 400, not the catch-all 500.
        private fun decode(text: String): String =
            try {
                URLDecoder.decode(text, StandardCharsets.UTF_8)
            } catch (expected: IllegalArgumentException) {
                throw InvalidRequestException("The query string is not correctly encoded")
            }
    }

    companion object {
        const val CREATE_PATH = "/rest/api/2/repository"
        const val TARGET_ATTRIBUTE = "provisioningTarget"
    }
}
