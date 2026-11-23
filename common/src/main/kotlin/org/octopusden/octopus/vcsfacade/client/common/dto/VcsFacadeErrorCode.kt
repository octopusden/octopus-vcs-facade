package org.octopusden.octopus.vcsfacade.client.common.dto

import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryConflictException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryEmptyException
import org.octopusden.octopus.vcsfacade.client.common.exception.RepositoryExistsException
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.client.common.exception.VcsFacadeException

enum class VcsFacadeErrorCode(
    private val getExceptionFunction: (message: String, state: RepositoryState?) -> VcsFacadeException,
    val defaultMessage: String,
) {
    OTHER({ message, _ -> VcsFacadeException(message) }, "Other"),
    NOT_FOUND({ message, _ -> NotFoundException(message) }, "Not Found"),
    ARGUMENTS_NOT_COMPATIBLE({ message, _ -> ArgumentsNotCompatibleException(message) }, "Arguments not compatible"),
    REPOSITORY_EXISTS({ message, state -> RepositoryExistsException(message, state) }, "Repository exists"),
    REPOSITORY_CONFLICT({ message, state -> RepositoryConflictException(message, state) }, "Repository conflict"),
    REPOSITORY_EMPTY({ message, _ -> RepositoryEmptyException(message) }, "Repository is empty"),
    INVALID_CONTENT({ message, _ -> InvalidContentException(message) }, "Invalid content"),
    INVALID_REQUEST({ message, _ -> InvalidRequestException(message) }, "Invalid request"),
    TARGET_NOT_ALLOWED({ message, _ -> TargetNotAllowedException(message) }, "Target not allowed"),
    ;

    fun getException(message: String): VcsFacadeException = getExceptionFunction.invoke(message, null)

    fun getException(
        message: String,
        state: RepositoryState?,
    ): VcsFacadeException = getExceptionFunction.invoke(message, state)
}
