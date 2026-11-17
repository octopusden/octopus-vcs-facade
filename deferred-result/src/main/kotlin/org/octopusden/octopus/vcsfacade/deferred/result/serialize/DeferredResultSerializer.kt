package org.octopusden.octopus.vcsfacade.deferred.result.serialize

import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResult
import java.lang.reflect.Type

interface DeferredResultSerializer {
    fun serialize(result: DeferredResult<*>): String

    fun <T : Any> deserialize(
        value: String,
        type: Type,
    ): DeferredResult<T>
}