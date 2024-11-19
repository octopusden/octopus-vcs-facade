package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.RetryResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeErrorCode
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.exception.InvalidSignatureException
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException as BitBucketNotFoundException
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException as GiteaNotFoundException

@ControllerAdvice
class ExceptionInfoHandler {
    @ExceptionHandler(JobProcessingException::class)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ResponseBody
    fun handleJobProcessing(exception: JobProcessingException): RetryResponse {
        log.debug("Request {} is processing", exception.requestId)
        return RetryResponse(exception.retryAfter, exception.requestId)
    }

    @ExceptionHandler(NotFoundException::class, GiteaNotFoundException::class, BitBucketNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    fun handleNotFound(exception: Exception) = handleError(VcsFacadeErrorCode.NOT_FOUND, exception)

    @ExceptionHandler(ArgumentsNotCompatibleException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleArgumentsNotCompatible(exception: ArgumentsNotCompatibleException) =
        handleError(VcsFacadeErrorCode.ARGUMENTS_NOT_COMPATIBLE, exception)

    @ExceptionHandler(InvalidSignatureException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    fun handleInvalidSignature(exception: InvalidSignatureException) = handleError(VcsFacadeErrorCode.OTHER, exception)

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @Order(100)
    fun handleException(exception: Exception) = handleError(VcsFacadeErrorCode.OTHER, exception)

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ExceptionInfoHandler::class.java)

        private fun handleError(errorCode: VcsFacadeErrorCode, exception: Exception) = ErrorResponse(
            errorCode, exception.message ?: errorCode.defaultMessage
        ).also {
            log.error(it.errorMessage, exception)
        }
    }
}
