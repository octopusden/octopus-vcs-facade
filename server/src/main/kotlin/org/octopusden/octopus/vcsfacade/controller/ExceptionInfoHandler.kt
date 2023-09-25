package org.octopusden.octopus.vcsfacade.controller

import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.RetryResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeErrorCode
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException
import org.octopusden.octopus.vcsfacade.exception.JobProcessingException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
class ExceptionInfoHandler {

    @ExceptionHandler(JobProcessingException::class)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ResponseBody
    fun handleJobProcessing(exception: JobProcessingException): RetryResponse {
        log.debug("Job is processing, request: ${exception.requestId}")
        return RetryResponse(exception.retryAfter, exception.requestId)
    }

    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleNotFound(exception: VcsFacadeException) = getErrorResponse(exception)

    @ExceptionHandler(ArgumentsNotCompatibleException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun handleArgumentsNotCompatible(exception: VcsFacadeException): ErrorResponse {
        log.error(exception.message)
        return getErrorResponse(exception)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @Order(100)
    fun handleException(exception: Exception): ErrorResponse {
        log.error(exception.message ?: "Internal error", exception)
        return getErrorResponse(exception)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ExceptionInfoHandler::class.java)

        private fun getErrorResponse(exception: Exception): ErrorResponse {
            val errorCode = VcsFacadeErrorCode.getErrorCode(exception)
            return ErrorResponse(
                errorCode, exception.message
                    ?: errorCode.simpleMessage
            )
        }
    }
}
