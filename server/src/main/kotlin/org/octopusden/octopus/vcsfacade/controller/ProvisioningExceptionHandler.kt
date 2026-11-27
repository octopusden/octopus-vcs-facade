package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeErrorCode
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.exception.ContentTooLargeException
import org.octopusden.octopus.vcsfacade.exception.ProvisioningNotImplementedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * Errors of the repository provisioning calls. Ordered before [ExceptionInfoHandler], whose catch-all would
 * otherwise answer these with 500. Scoped to [ProvisioningController] so the other endpoints keep their behaviour.
 */
@ControllerAdvice(assignableTypes = [ProvisioningController::class])
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

    @ExceptionHandler(TargetNotAllowedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    fun handleTargetNotAllowed(exception: TargetNotAllowedException): ErrorResponse {
        log.warn("Target not allowed: {}", exception.message)
        return ErrorResponse(
            VcsFacadeErrorCode.TARGET_NOT_ALLOWED,
            exception.message ?: VcsFacadeErrorCode.TARGET_NOT_ALLOWED.defaultMessage,
        )
    }

    @ExceptionHandler(InvalidRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleInvalidRequest(exception: InvalidRequestException): ErrorResponse {
        log.warn("Invalid request: {}", exception.message)
        return ErrorResponse(
            VcsFacadeErrorCode.INVALID_REQUEST,
            exception.message ?: VcsFacadeErrorCode.INVALID_REQUEST.defaultMessage,
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ErrorResponse {
        log.warn("Invalid request: {}", exception.message)
        return ErrorResponse(
            VcsFacadeErrorCode.INVALID_REQUEST,
            exception.message,
        )
    }

    @ExceptionHandler(InvalidContentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleInvalidContent(exception: InvalidContentException): ErrorResponse {
        // The message names a caller-supplied entry, so it goes to the response, not to the log.
        log.warn("Invalid content")
        return ErrorResponse(
            VcsFacadeErrorCode.INVALID_CONTENT,
            exception.message ?: VcsFacadeErrorCode.INVALID_CONTENT.defaultMessage,
        )
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ResponseBody
    fun handleUploadTooLarge(exception: MaxUploadSizeExceededException): ErrorResponse {
        log.warn("Upload too large: {}", exception.message)
        return ErrorResponse(VcsFacadeErrorCode.INVALID_CONTENT, UPLOAD_TOO_LARGE)
    }

    // Defensive: the container's limit normally answers first, and the validator checks the same size again.
    @ExceptionHandler(ContentTooLargeException::class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ResponseBody
    fun handleContentTooLarge(): ErrorResponse {
        log.warn("Content too large")
        return ErrorResponse(VcsFacadeErrorCode.INVALID_CONTENT, UPLOAD_TOO_LARGE)
    }

    // Temporary, until the create is implemented.
    @ExceptionHandler(ProvisioningNotImplementedException::class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    @ResponseBody
    fun handleNotImplemented(exception: ProvisioningNotImplementedException): ErrorResponse {
        log.warn("Not implemented: {}", exception.message)
        return ErrorResponse(
            VcsFacadeErrorCode.OTHER,
            exception.message ?: VcsFacadeErrorCode.OTHER.defaultMessage,
        )
    }

    companion object {
        private const val UPLOAD_TOO_LARGE = "The content is larger than the limit"
        private val log: Logger = LoggerFactory.getLogger(ProvisioningExceptionHandler::class.java)
    }
}
