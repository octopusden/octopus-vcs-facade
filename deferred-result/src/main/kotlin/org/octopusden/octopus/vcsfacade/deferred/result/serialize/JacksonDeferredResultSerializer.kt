package org.octopusden.octopus.vcsfacade.deferred.result.serialize

import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResult
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus
import java.lang.reflect.Type

class JacksonDeferredResultSerializer(
    private val objectMapper: ObjectMapper,
) : DeferredResultSerializer {
    override fun serialize(result: DeferredResult<*>): String {
        val value =
            if (result.result === Unit) {
                result.copy(result = null)
            } else {
                result
            }

        return objectMapper.writeValueAsString(value)
    }

    override fun <T : Any> deserialize(
        value: String,
        type: Type,
    ): DeferredResult<T> {
        if (type == Unit::class.java) {
            return deserializeUnit(value)
        }
        val resultType = objectMapper.typeFactory.constructType(type)

        val deferredResultType =
            objectMapper.typeFactory.constructParametricType(
                DeferredResult::class.java,
                resultType,
            )

        return objectMapper.readValue(
            value,
            deferredResultType,
        )
    }

    private fun <T : Any> deserializeUnit(value: String): DeferredResult<T> {
        val result =
            objectMapper.readValue(
                value,
                DeferredResult::class.java,
            )

        @Suppress("UNCHECKED_CAST")
        return DeferredResult(
            requestId = result.requestId,
            status = result.status,
            result =
                if (result.status == DeferredResultStatus.COMPLETED) {
                    Unit as T
                } else {
                    null
                },
            error = result.error,
            ownerId = result.ownerId,
        )
    }
}
