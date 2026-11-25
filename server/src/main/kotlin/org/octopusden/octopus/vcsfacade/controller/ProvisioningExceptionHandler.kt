package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeErrorCode
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
    fun handleNotImplemented(exception: ProvisioningNotImplementedException): ErrorResponse {
        log.info("Not implemented: {}", exception.message)
        return ErrorResponse(VcsFacadeErrorCode.OTHER, exception.message ?: "Not Implemented")
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ProvisioningExceptionHandler::class.java)
    }
}
