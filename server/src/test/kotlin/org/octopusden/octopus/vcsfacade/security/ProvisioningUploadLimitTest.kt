package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.cloud.commons.security.client.dto.UserInfo
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.TestZips
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.octopusden.octopus.vcsfacade.service.serviceProperties
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Provisioning on, on a real servlet container: the upload limits are enforced by the container, which MockMvc
 * does not do. The limit is 1 KB here.
 */
@SpringBootTest(
    classes = [ExistingEndpointsTest.WebTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "vcs-facade.provisioning.enabled=true",
        "vcs-facade.provisioning.max-content-size=1KB",
        "auth-server.url=http://localhost:0",
        "auth-server.realm=test",
        "octopus-security.roles.ROLE_PROVISIONER=CREATE_REPOSITORIES",
    ],
)
@ImportAutoConfiguration(
    ServletWebServerFactoryAutoConfiguration::class,
    DispatcherServletAutoConfiguration::class,
    WebMvcAutoConfiguration::class,
    HttpMessageConvertersAutoConfiguration::class,
    JacksonAutoConfiguration::class,
    MultipartAutoConfiguration::class,
    SecurityAutoConfiguration::class,
    SecurityFilterAutoConfiguration::class,
)
class ProvisioningUploadLimitTest {
    @LocalServerPort
    private var port = 0

    @MockBean
    private lateinit var vcsManager: VcsManager

    @MockBean
    private lateinit var authServerClient: AuthServerClient

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    private fun upload(
        fileSize: Int,
        token: String? = "provisioner",
        content: ByteArray = ByteArray(fileSize) { 'a'.code.toByte() },
    ): HttpResponse<String> {
        `when`(
            jwtDecoder.decode(anyString()),
        ).thenAnswer {
            Jwt
                .withTokenValue(it.getArgument(0))
                .header("alg", "none")
                .subject("u")
                .build()
        }
        `when`(authServerClient.getUserInfo("provisioner")).thenReturn(UserInfo(listOf("PROVISIONER"), emptyList()))
        val service = GiteaService(
            serviceProperties(VcsServiceType.GITEA, Provisioning(token = "t", allowedGroups = setOf("team-a"))),
            mock(GiteaClient::class.java),
        )
        `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenReturn(service)
        val boundary = "test-boundary"
        val body = ByteArrayOutputStream()
            .apply {
                fun text(value: String) = write(value.toByteArray(Charsets.ISO_8859_1))
                listOf("marker" to "m-1", "branch" to "main").forEach { (name, value) ->
                    text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                text("--$boundary\r\nContent-Disposition: form-data; name=\"content\"; filename=\"content.zip\"\r\n")
                text("Content-Type: application/zip\r\n\r\n")
                write(content)
                text("\r\n--$boundary--\r\n")
            }.toByteArray()
        val request = HttpRequest
            .newBuilder(URI("http://localhost:$port/rest/api/2/repository?sshUrl=ssh%3A%2F%2Fgit%40gitea.example.org%2Fteam-a%2Frepo.git"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `an upload over the limit answers 413 INVALID_CONTENT as JSON, not 500`() {
        val response = upload(fileSize = 4 * 1024)
        assertEquals(413, response.statusCode(), response.body())
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        assertTrue(contentType.startsWith("application/json"), contentType)
        assertTrue(response.body().contains("\"errorCode\":\"INVALID_CONTENT\""), response.body())
    }

    @Test
    fun `an upload within the limit reaches the handler`() {
        val response = upload(fileSize = 0, content = TestZips.zip { file("a.txt", "x") })
        assertEquals(501, response.statusCode(), response.body())
    }

    @Test
    fun `an upload over the limit without a token is still 401`() {
        assertEquals(401, upload(fileSize = 4 * 1024, token = null).statusCode())
    }
}
