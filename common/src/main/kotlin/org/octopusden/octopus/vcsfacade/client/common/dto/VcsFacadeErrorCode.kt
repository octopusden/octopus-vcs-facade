package org.octopusden.octopus.vcsfacade.client.common.dto

import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException

enum class VcsFacadeErrorCode(
    private val getExceptionFunction: (message: String) -> VcsFacadeException,
    val defaultMessage: String
) {
    OTHER({ message -> VcsFacadeException(message) }, "Other"),
    NOT_FOUND({ message -> NotFoundException(message) }, "Not Found"),
    ARGUMENTS_NOT_COMPATIBLE({ message -> ArgumentsNotCompatibleException(message) }, "Arguments not compatible");

    fun getException(message: String): VcsFacadeException {
        return getExceptionFunction.invoke(message)
    }
}
