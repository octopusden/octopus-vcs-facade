package org.octopusden.octopus.vcsfacade.deferred.result.core

import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResult
import java.lang.reflect.Type

interface DeferredResultManager {
    fun <T : Any> execute(
        requestId: String,
        type: Type,
        operation: () -> T,
    ): DeferredResult<T>

    fun <T : Any> get(
        requestId: String,
        type: Type,
    ): DeferredResult<T>?
}
