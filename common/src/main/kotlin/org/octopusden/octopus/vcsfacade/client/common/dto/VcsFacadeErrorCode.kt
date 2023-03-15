package org.octopusden.octopus.vcsfacade.client.common.dto

import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException

enum class VcsFacadeErrorCode(private val function: (message: String) -> Exception, val simpleMessage: String) {
    OTHER({ m -> IllegalStateException(m) }, "Internal server error"),
    NOT_FOUND({ m -> NotFoundException(m) }, "Not Found"),
    ARGUMENTS_NOT_COMPATIBLE({ m -> ArgumentsNotCompatibleException(m) }, "Arguments not compatible");

    fun getException(message: String): Exception {
        return function.invoke(message)
    }

    companion object {
        fun getErrorCode(exception: Exception): VcsFacadeErrorCode {
            val qualifiedName = exception::class.qualifiedName
            return values().find { v -> v.function.invoke("")::class.qualifiedName == qualifiedName }
                ?: OTHER
        }
    }
}
