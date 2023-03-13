package org.octopusden.task

import com.google.common.io.Files
import com.google.common.net.HttpHeaders
import org.apache.http.entity.ContentType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import java.io.File
import java.nio.charset.Charset


open class MigrateMockData : DefaultTask() {
    companion object {
        private val endpointToResponseFileName = mapOf(
            ("/gitlab/api/v4/projects/test-data/vcs-facade-healthcheck" to emptyMap<String, String>()) to "gitlab-project.json",
            ("/gitlab/api/v4/projects/test-data%2Fvcs-facade-healthcheck" to emptyMap<String, String>()) to "gitlab-project.json",
            ("/gitlab/api/v4/projects/196/repository/commits" to emptyMap<String, String>()) to "gitlab-commits.json",
            ("/gitlab/api/v4/projects/196/repository/tags" to emptyMap<String, String>()) to "gitlab-tags.json",
            ("/gitlab/api/v4/projects/196/repository/commits/9320183f5d5f5868fdb82b36e3abd6f9d1424114" to emptyMap<String, String>()) to "gitlab-commit.json",
            ("/gitlab/api/v4/projects/196/repository/commits/00cc61dd4c3eca64d12e6beceff1a40a436962f5" to emptyMap<String, String>()) to "gitlab-commit-1.json",
            ("/gitlab/api/v4/projects/196/repository/commits/321d4908aef10bafa1488f9b053270acc29f3d78" to emptyMap<String, String>()) to "gitlab-commit-321d.json",
        )
    }

    private val mockServerClient = MockServerClient("localhost", 1080)

    @get:Input
    lateinit var testDataDir: String

    @TaskAction
    fun startMockServer() {
        mockServerClient.reset()
        endpointToResponseFileName.forEach {
            generateMockserverData(it.key.first, it.key.second, testDataDir + File.separator + it.value, 200)
        }
    }

    private fun generateMockserverData(endpoint: String, params: Map<String, String>, filename: String, status: Int) {
        val body = Files.asCharSource(File(filename), Charset.defaultCharset()).read()
        val request = HttpRequest.request()
            .withMethod("GET")
            .withPath(endpoint)
        params.forEach {
            request.withQueryStringParameter(it.key, it.value)
        }
        mockServerClient.`when`(request)
            .respond {
                logger.debug(
                    "MockServer request: ${it.method} ${it.path} ${it.queryStringParameterList.joinToString(",")} ${
                        it.pathParameterList.joinToString(
                            ","
                        )
                    }"
                )
                HttpResponse.response()
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                    .withHeader("x-page", "1")
                    .withHeader("X-Per-Page", "20")
                    .withHeader("x-total", "3")
                    .withHeader("x-total-pages", "1")
                    .withBody(body)
                    .withStatusCode(status)
            }
    }
}
