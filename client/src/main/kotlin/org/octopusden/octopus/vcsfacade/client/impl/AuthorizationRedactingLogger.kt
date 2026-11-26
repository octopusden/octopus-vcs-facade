package org.octopusden.octopus.vcsfacade.client.impl

import feign.slf4j.Slf4jLogger
import org.slf4j.Logger

/**
 * Feign logger that keeps the full request/response logging but never writes the value of an
 * `Authorization` header, so the bearer token does not end up in the logs.
 */
class AuthorizationRedactingLogger(
    logger: Logger,
) : Slf4jLogger(logger) {
    override fun log(
        configKey: String,
        format: String,
        vararg args: Any?,
    ) {
        // feign logs every header as "%s: %s" (name, value)
        if (format == HEADER_FORMAT && args.size == 2 && AUTHORIZATION.equals(args[0].toString(), ignoreCase = true)) {
            super.log(configKey, format, args[0], REDACTED)
        } else {
            super.log(configKey, format, *args)
        }
    }

    private companion object {
        const val HEADER_FORMAT = "%s: %s"
        const val AUTHORIZATION = "Authorization"
        const val REDACTED = "***"
    }
}
