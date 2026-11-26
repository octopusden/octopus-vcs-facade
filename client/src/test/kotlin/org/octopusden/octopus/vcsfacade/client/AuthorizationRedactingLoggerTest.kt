package org.octopusden.octopus.vcsfacade.client

import feign.Feign
import feign.Logger
import feign.RequestLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.vcsfacade.client.impl.AuthorizationRedactingLogger
import java.lang.reflect.Proxy

class AuthorizationRedactingLoggerTest {
    interface Api {
        @RequestLine("GET /ping")
        fun ping(): String
    }

    private val logged = mutableListOf<String>()

    // a debug-enabled SLF4J logger that records every message
    private val slf4jLogger = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(org.slf4j.Logger::class.java),
    ) { _, method, args ->
        when (method.name) {
            "isDebugEnabled" -> true
            "debug" -> {
                logged += args[0] as String
                null
            }
            else -> null
        }
    } as org.slf4j.Logger

    private fun call(headerName: String): RecordingServer.Request {
        RecordingServer().use { server ->
            server.responseBody = "pong"
            server.responseContentType = "text/plain"
            Feign
                .builder()
                .logger(AuthorizationRedactingLogger(slf4jLogger))
                .logLevel(Logger.Level.FULL)
                .requestInterceptor { it.header(headerName, "Bearer s3cret-token") }
                .requestInterceptor { it.header("X-Other", "visible") }
                .target(Api::class.java, server.url)
                .ping()
            return server.requests.last()
        }
    }

    @Test
    fun `authorization value is not logged but still sent`() {
        val request = call("Authorization")
        assertEquals("Bearer s3cret-token", request.headers["authorization"])
        assertTrue(logged.any { it.endsWith("Authorization: ***") }, logged.toString())
        assertFalse(logged.any { it.contains("s3cret-token") }, logged.toString())
    }

    @Test
    fun `authorization header name is matched case-insensitively`() {
        call("AUTHORIZATION")
        assertFalse(logged.any { it.contains("s3cret-token") }, logged.toString())
    }

    @Test
    fun `other headers and bodies are still logged`() {
        call("Authorization")
        assertTrue(logged.any { it.endsWith("X-Other: visible") }, logged.toString())
        assertTrue(logged.any { it.endsWith("pong") }, logged.toString())
    }
}
