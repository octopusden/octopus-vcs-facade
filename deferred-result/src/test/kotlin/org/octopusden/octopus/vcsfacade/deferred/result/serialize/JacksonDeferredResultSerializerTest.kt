package org.octopusden.octopus.vcsfacade.deferred.result.serialize

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResult
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultError
import org.octopusden.octopus.vcsfacade.deferred.result.core.model.DeferredResultStatus

class JacksonDeferredResultSerializerTest {
    private val objectMapper = jacksonObjectMapper()
    private val serializer = JacksonDeferredResultSerializer(objectMapper)

    @Test
    fun serializesAndDeserializesStringResult() {
        val result = DeferredResult(
            requestId = "request-1",
            status = DeferredResultStatus.COMPLETED,
            result = "value",
        )

        val serialized = serializer.serialize(result)
        val deserialized =
            serializer.deserialize<String>(
                serialized,
                String::class.java,
            )

        assertEquals(result, deserialized)
    }

    @Test
    fun serializesAndDeserializesParameterizedResult() {
        val result = DeferredResult(
            requestId = "request-1",
            status = DeferredResultStatus.COMPLETED,
            result = listOf("a", "b"),
        )
        val type = object : TypeReference<List<String>>() {}.type

        val serialized = serializer.serialize(result)
        val deserialized =
            serializer.deserialize<List<String>>(
                serialized,
                type,
            )

        assertEquals(result, deserialized)
    }

    @Test
    fun serializesUnitResultAsNull() {
        val result = DeferredResult(
            requestId = "request-1",
            status = DeferredResultStatus.COMPLETED,
            result = Unit,
        )

        val serialized = serializer.serialize(result)
        val json = objectMapper.readTree(serialized)

        assertTrue(json["result"].isNull)
    }

    @Test
    fun deserializesCompletedUnitResult() {
        val result = DeferredResult(
            requestId = "request-1",
            status = DeferredResultStatus.COMPLETED,
            result = Unit,
        )

        val serialized = serializer.serialize(result)
        val deserialized =
            serializer.deserialize<Unit>(
                serialized,
                Unit::class.java,
            )

        assertEquals("request-1", deserialized.requestId)
        assertEquals(DeferredResultStatus.COMPLETED, deserialized.status)
        assertEquals(Unit, deserialized.result)
        assertNull(deserialized.error)
    }

    @Test
    fun doesNotRestoreUnitForFailedResult() {
        val result = DeferredResult<Unit>(
            requestId = "request-1",
            status = DeferredResultStatus.FAILED,
            error = DeferredResultError(code = "java.lang.RuntimeException", message = "boom"),
        )

        val serialized = serializer.serialize(result)
        val deserialized =
            serializer.deserialize<Unit>(
                serialized,
                Unit::class.java,
            )

        assertEquals("request-1", deserialized.requestId)
        assertEquals(DeferredResultStatus.FAILED, deserialized.status)
        assertNull(deserialized.result)
        assertEquals("boom", deserialized.error?.message)
        assertEquals("java.lang.RuntimeException", deserialized.error?.code)
    }

    @Test
    fun serializesAndDeserializesComplexResult() {
        val result = DeferredResult(
            requestId = "request-1",
            status = DeferredResultStatus.COMPLETED,
            result = TestResult(
                id = 42,
                name = "test",
                metadata = Metadata(
                    source = "vcs",
                    description = null,
                ),
                items = listOf(
                    Item(
                        key = "first",
                        value = 10,
                    ),
                    Item(
                        key = "second",
                        value = 20,
                    ),
                ),
            ),
        )

        val serialized = serializer.serialize(result)

        val deserialized =
            serializer.deserialize<TestResult>(
                serialized,
                TestResult::class.java,
            )

        assertEquals(result, deserialized)
    }

    private data class TestResult(
        val id: Long,
        val name: String,
        val metadata: Metadata,
        val items: List<Item>,
    )

    private data class Metadata(
        val source: String,
        val description: String?,
    )

    private data class Item(
        val key: String,
        val value: Int,
    )
}
