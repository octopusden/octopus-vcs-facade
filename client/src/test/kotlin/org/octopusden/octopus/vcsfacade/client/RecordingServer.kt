package org.octopusden.octopus.vcsfacade.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * A throw-away HTTP server that records every request and answers with a canned response.
 */
class RecordingServer : AutoCloseable {
    class Request(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    var status = 200
    var responseBody = "{}"
    var responseContentType = "application/json"
    val requests = mutableListOf<Request>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
        server.createContext("/") { exchange ->
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
            requests += Request(
                exchange.requestMethod,
                exchange.requestURI.rawPath,
                exchange.requestURI.rawQuery,
                headers,
                exchange.requestBody.readBytes(),
            )
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", responseContentType)
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    val url get() = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}
