package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeErrorCode
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.exception.ProvisioningNotImplementedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Errors of the repository provisioning calls. Ordered before [ExceptionInfoHandler], whose catch-all would
 * otherwise answer these with 500.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProvisioningExceptionHandler {
    @ExceptionHandler(TargetNotAllowedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    fun handleTargetNotAllowed(exception: TargetNotAllowedException) = handleError(VcsFacadeErrorCode.TARGET_NOT_ALLOWED, exception)

    // Thrown by `@PreAuthorize` inside the controller call, where Spring Security's filter no longer sees it.
    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    fun handleAccessDenied(exception: AccessDeniedException): ErrorResponse {
        log.debug("Access denied", exception)
        return ErrorResponse(VcsFacadeErrorCode.OTHER, exception.message ?: "Forbidden")
    }

    @ExceptionHandler(ProvisioningNotImplementedException::class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    @ResponseBody
    fun handleNotImplemented(exception: ProvisioningNotImplementedException) = handleError(VcsFacadeErrorCode.OTHER, exception)

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ProvisioningExceptionHandler::class.java)

        private fun handleError(
            errorCode: VcsFacadeErrorCode,
            exception: Exception,
        ) = ErrorResponse(
            errorCode,
            exception.message ?: errorCode.defaultMessage,
        ).also {
            log.error(it.errorMessage, exception)
        }
    }
}
