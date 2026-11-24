package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaOrganization
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.createService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.octopusden.octopus.vcsfacade.service.serviceProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException as GiteaNotFoundException

/**
 * Provisioning on, the auth server replaced by a mock: the roles come from the token's authorities,
 * the permission from `octopus-security.roles`.
 */
@WebMvcTest(
    properties = [
        "vcs-facade.provisioning.enabled=true",
        "auth-server.url=http://localhost:0",
        "auth-server.realm=test",
        "octopus-security.roles.ROLE_PROVISIONER=CREATE_REPOSITORIES",
    ],
)
@ContextConfiguration(classes = [ExistingEndpointsTest.WebTestConfig::class])
@MockBean(AuthServerClient::class)
class ProvisioningEnabledTest : ExistingEndpointsTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val provisioningCalls = listOf(
        HttpMethod.GET to "/rest/api/2/groups",
        HttpMethod.GET to "/rest/api/2/repository/state",
        HttpMethod.POST to "/rest/api/2/repository",
    )

    private val provisioner = jwt().authorities(SimpleGrantedAuthority("ROLE_PROVISIONER"))
    private val stranger = jwt().authorities(SimpleGrantedAuthority("ROLE_SOMETHING_ELSE"))

    private fun call(
        method: HttpMethod,
        path: String,
        sshUrl: String = ALLOWED_URL,
        auth: RequestPostProcessor? = null,
    ): ResultActions =
        mockMvc.perform(
            // The create takes multipart; the others have no body.
            (if (method == HttpMethod.POST) multipart(method, path) else request(method, path))
                .param("sshUrl", sshUrl)
                .accept(MediaType.APPLICATION_JSON)
                .also { builder ->
                    auth?.let { builder.with(it) }
                },
        )

    @Test
    fun `no token answers 401 with a JSON body on every provisioning call`() {
        provisioningCalls.forEach { (method, path) ->
            call(method, path)
                .andExpect(status().isUnauthorized)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty)
        }
    }

    @Test
    fun `a rejected token answers 401 with a JSON body`() {
        mockMvc
            .perform(
                request(HttpMethod.GET, "/rest/api/2/groups")
                    .param("sshUrl", ALLOWED_URL)
                    .header("Authorization", "Bearer not-a-jwt"),
            ).andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorMessage").isNotEmpty)
    }

    @Test
    fun `a token without the permission answers 403 with a JSON body on every provisioning call`() {
        provisioningCalls.forEach { (method, path) ->
            call(method, path, auth = stranger)
                .andExpect(status().isForbidden)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty)
        }
    }

    @Test
    fun `a token with the permission reaches the handler on the create`() {
        serveAllowedGroup()
        provisioningCalls.filter { it.first == HttpMethod.POST }.forEach { (method, path) ->
            call(method, path, auth = provisioner).andExpect(status().isNotImplemented)
        }
    }

    @Test
    fun `the group check answers exists true for a group the provider finds`() {
        serveAllowedGroup().also { `when`(it.getOrganization("team-a")).thenReturn(mock(GiteaOrganization::class.java)) }
        call(HttpMethod.GET, "/rest/api/2/groups", auth = provisioner)
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.exists").value(true))
    }

    @Test
    fun `the group check answers exists false when the provider does not find the group`() {
        serveAllowedGroup().also { `when`(it.getOrganization("team-a")).thenThrow(GiteaNotFoundException("no such organisation")) }
        call(HttpMethod.GET, "/rest/api/2/groups", auth = provisioner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exists").value(false))
    }

    @Test
    fun `the group check answers 5xx and never exists false when the provider fails`() {
        serveAllowedGroup().also { `when`(it.getOrganization("team-a")).thenThrow(IllegalStateException("provider down")) }
        call(HttpMethod.GET, "/rest/api/2/groups", auth = provisioner)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
            .andExpect(jsonPath("$.exists").doesNotExist())
    }

    @Test
    fun `the state read answers PRESENT with its fields`() {
        val client = serveAllowedGroup()
        `when`(client.getRepository("team-a", "repo")).thenReturn(
            GiteaRepository(
                id = 1,
                name = "repo",
                fullName = "team-a/repo",
                avatarUrl = "",
                description = "m-1",
                defaultBranch = "main",
                empty = true,
            ),
        )
        call(HttpMethod.GET, "/rest/api/2/repository/state", auth = provisioner)
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("PRESENT"))
            .andExpect(jsonPath("$.marker").value("m-1"))
            .andExpect(jsonPath("$.defaultBranch").value("main"))
            .andExpect(jsonPath("$.hasCommits").value(false))
            .andExpect(jsonPath("$.rootCommitMarker").doesNotExist())
    }

    @Test
    fun `the state read answers MISSING when the provider does not find the repository`() {
        val client = serveAllowedGroup()
        `when`(client.getRepository("team-a", "repo")).thenThrow(GiteaNotFoundException("no such repository"))
        call(HttpMethod.GET, "/rest/api/2/repository/state", auth = provisioner)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("MISSING"))
            .andExpect(jsonPath("$.hasCommits").value(false))
    }

    @Test
    fun `the state read answers 500 OTHER and no status when the provider fails`() {
        val client = serveAllowedGroup()
        `when`(client.getRepository("team-a", "repo")).thenThrow(IllegalStateException("provider down"))
        call(HttpMethod.GET, "/rest/api/2/repository/state", auth = provisioner)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
            .andExpect(jsonPath("$.status").doesNotExist())
    }

    @Test
    fun `a multipart create with a zip file part reaches the handler`() {
        serveAllowedGroup()
        mockMvc
            .perform(
                multipart("/rest/api/2/repository")
                    .file(MockMultipartFile("content", "content.zip", "application/zip", "zip-bytes".toByteArray()))
                    .param("sshUrl", ALLOWED_URL)
                    .param("marker", "m")
                    .param("branch", "main")
                    .param("commitMessage", "first")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(provisioner),
            ).andExpect(status().isNotImplemented)
    }

    @Test
    fun `a group outside the allow-list answers 403 TARGET_NOT_ALLOWED on every provisioning call`() {
        serveAllowedGroup()
        provisioningCalls.forEach { (method, path) ->
            call(method, path, sshUrl = "ssh://git@gitea.example.org/other/repo.git", auth = provisioner)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
        }
    }

    @Test
    fun `a VCS without a provisioning credential answers 403 TARGET_NOT_ALLOWED`() {
        `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenReturn(createService(VcsServiceType.GITEA, null))
        provisioningCalls.forEach { (method, path) ->
            call(method, path, auth = provisioner)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
        }
    }

    @Test
    fun `the permission is checked before the target`() {
        serveAllowedGroup()
        call(HttpMethod.GET, "/rest/api/2/groups", sshUrl = "ssh://git@gitea.example.org/other/repo.git", auth = stranger)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
    }

    @Test
    fun `only the exact provisioning paths need a token`() {
        // Same prefix, other method or other path: still open, so they reach the existing controller.
        call(HttpMethod.GET, "/rest/api/2/repository/tags").andExpect(status().`is`(org.hamcrest.Matchers.not(401)))
        call(HttpMethod.POST, "/rest/api/2/repository/tags").andExpect(status().`is`(org.hamcrest.Matchers.not(401)))
    }

    /**
     * Serves a Gitea service that allows `team-a`; the returned mock is its provisioning client.
     */
    private fun serveAllowedGroup(): GiteaClient {
        val client = mock(GiteaClient::class.java)
        val provisioning = Provisioning(token = "t", allowedGroups = setOf("team-a"))
        val service = GiteaService(serviceProperties(VcsServiceType.GITEA, provisioning), client)
        `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenReturn(service)
        return client
    }

    companion object {
        private const val ALLOWED_URL = "ssh://git@gitea.example.org/team-a/repo.git"
    }
}
