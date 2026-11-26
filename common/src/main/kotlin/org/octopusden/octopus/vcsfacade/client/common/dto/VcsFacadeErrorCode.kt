package org.octopusden.octopus.vcsfacade.client.common.dto

import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException

enum class VcsFacadeErrorCode(
    private val getExceptionFunction: (message: String) -> VcsFacadeException,
    val defaultMessage: String,
) {
    OTHER({ message -> VcsFacadeException(message) }, "Other"),
    NOT_FOUND({ message -> NotFoundException(message) }, "Not Found"),
    ARGUMENTS_NOT_COMPATIBLE({ message -> ArgumentsNotCompatibleException(message) }, "Arguments not compatible"),
    INVALID_CONTENT({ message -> InvalidContentException(message) }, "Invalid content"),
    INVALID_REQUEST({ message -> InvalidRequestException(message) }, "Invalid request"),
    TARGET_NOT_ALLOWED({ message -> TargetNotAllowedException(message) }, "Target not allowed"),
    ;

    fun getException(message: String): VcsFacadeException = getExceptionFunction.invoke(message)
}
