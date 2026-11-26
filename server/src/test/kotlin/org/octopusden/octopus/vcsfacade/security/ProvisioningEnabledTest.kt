package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaOrganization
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.TestZips
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.octopusden.octopus.vcsfacade.service.serviceProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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

    private val provisioningPaths = listOf("/rest/api/2/groups", "/rest/api/2/repository/state")

    private val provisioner = jwt().authorities(SimpleGrantedAuthority("ROLE_PROVISIONER"))
    private val stranger = jwt().authorities(SimpleGrantedAuthority("ROLE_SOMETHING_ELSE"))

    private fun call(
        path: String,
        auth: RequestPostProcessor? = null,
        sshUrl: String = SSH_URL,
    ): ResultActions =
        mockMvc.perform(
            get(path)
                .param("sshUrl", sshUrl)
                .accept(MediaType.APPLICATION_JSON)
                .also { builder -> auth?.let { builder.with(it) } },
        )

    // No CSRF token anywhere below: the create is exempted from it.
    private fun create(
        auth: RequestPostProcessor? = null,
        sshUrl: String = SSH_URL,
        marker: String? = "m-1",
        branch: String? = "main",
    ): ResultActions =
        mockMvc.perform(
            multipart("/rest/api/2/repository")
                .file(MockMultipartFile("content", "content.zip", "application/zip", TestZips.zip { file("a.txt") }))
                .param("sshUrl", sshUrl)
                .also { builder ->
                    marker?.let { builder.param("marker", it) }
                    branch?.let { builder.param("branch", it) }
                }.accept(MediaType.APPLICATION_JSON)
                .also { builder -> auth?.let { builder.with(it) } },
        )

    @Test
    fun `create without a token answers 401 with a JSON body`() {
        create()
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorMessage").isNotEmpty)
    }

    @Test
    fun `create with a token without the permission answers 403 with a JSON body`() {
        create(stranger)
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorMessage").isNotEmpty)
    }

    @Test
    fun `create for a group outside the allow-list answers 403 TARGET_NOT_ALLOWED and calls no VCS`() {
        val client = serveAllowedGroup()
        create(provisioner, "ssh://git@gitea.example.org/other/repo.git")
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
        verifyNoInteractions(client)
    }

    @Test
    fun `create for an allowed group answers 501 with a JSON body and calls no VCS`() {
        val client = serveAllowedGroup()
        create(provisioner, ALLOWED_URL)
            .andExpect(status().isNotImplemented)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
        verifyNoInteractions(client)
    }

    @Test
    fun `create without any content part still answers 501`() {
        serveAllowedGroup()
        mockMvc
            .perform(
                multipart("/rest/api/2/repository")
                    .param("sshUrl", ALLOWED_URL)
                    .param("marker", "m-1")
                    .param("branch", "main")
                    .with(provisioner),
            ).andExpect(status().isNotImplemented)
    }

    @Test
    fun `create with an invalid marker or a missing branch answers 400 INVALID_REQUEST and calls no VCS`() {
        val client = serveAllowedGroup()
        listOf("m 1\nx" to "main", " m-1" to "main", "m".repeat(256) to "main", "m-1" to null, "m-1" to " ", null to "main").forEach {
            create(provisioner, ALLOWED_URL, it.first, it.second)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        }
        verifyNoInteractions(client)
    }

    private fun createWithZip(
        zip: ByteArray,
        sshUrl: String = ALLOWED_URL,
        marker: String = "m-1",
    ) = mockMvc.perform(
        multipart("/rest/api/2/repository")
            .file(MockMultipartFile("content", "content.zip", "application/zip", zip))
            .param("sshUrl", sshUrl)
            .param("marker", marker)
            .param("branch", "main")
            .accept(MediaType.APPLICATION_JSON)
            .with(provisioner),
    )

    @Test
    fun `create with an invalid zip answers 400 INVALID_CONTENT naming the entry and calls no VCS`() {
        val client = serveAllowedGroup()
        createWithZip(TestZips.zip { file("ok.txt").file("a/.git/HEAD") })
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value("INVALID_CONTENT"))
            .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("a/.git/HEAD")))
        createWithZip("not a zip".toByteArray())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("INVALID_CONTENT"))
        verifyNoInteractions(client)
    }

    @Test
    fun `create with a valid zip answers 501 and calls no VCS`() {
        val client = serveAllowedGroup()
        createWithZip(TestZips.zip { executable("run.sh").file("src/a.txt").symlink("link", "src/a.txt") })
            .andExpect(status().isNotImplemented)
        verifyNoInteractions(client)
    }

    @Test
    fun `the request is checked before the zip, the allow-list before both`() {
        serveAllowedGroup()
        val badZip = TestZips.zip { file("../x") }
        createWithZip(badZip, marker = " bad")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        createWithZip(badZip, sshUrl = "ssh://git@gitea.example.org/other/repo.git", marker = " bad")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
    }

    @Test
    fun `the allow-list is checked before the request`() {
        serveAllowedGroup()
        create(provisioner, "ssh://git@gitea.example.org/other/repo.git", marker = " bad", branch = null)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
    }

    @Test
    fun `no token answers 401 with a JSON body on both reads`() {
        provisioningPaths.forEach { path ->
            call(path)
                .andExpect(status().isUnauthorized)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty)
        }
    }

    @Test
    fun `a HEAD without a token answers 401 on both reads`() {
        // Spring MVC serves HEAD from the GET handlers, so security has to cover it too.
        provisioningPaths.forEach { path ->
            mockMvc.perform(head(path).param("sshUrl", SSH_URL)).andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `a rejected token answers 401 with a JSON body`() {
        mockMvc
            .perform(
                get("/rest/api/2/groups")
                    .param("sshUrl", SSH_URL)
                    .header("Authorization", "Bearer not-a-jwt"),
            ).andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorMessage").isNotEmpty)
    }

    @Test
    fun `a token without the permission answers 403 with a JSON body on both reads`() {
        provisioningPaths.forEach { path ->
            call(path, stranger)
                .andExpect(status().isForbidden)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty)
        }
    }

    @Test
    fun `a wrong method on a provisioning path passes security and answers 405`() {
        provisioningPaths.forEach { path ->
            mockMvc.perform(post(path).param("sshUrl", SSH_URL)).andExpect(status().isMethodNotAllowed)
        }
        mockMvc.perform(put("/rest/api/2/repository").param("sshUrl", SSH_URL)).andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `an existing GET carries no Spring Security response headers`() {
        `when`(vcsManager.getRepository(anyString())).thenReturn(Repository(SSH_URL, "http://host/team-a/repo"))
        val response = mockMvc.perform(get("/rest/api/2/repository").param("sshUrl", SSH_URL)).andReturn().response
        listOf("Cache-Control", "Pragma", "Expires", "X-Content-Type-Options", "X-Frame-Options", "X-XSS-Protection")
            .forEach { assertFalse(response.containsHeader(it), "$it is set: ${response.getHeaders(it)}") }
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
        call("/rest/api/2/repository/state", provisioner, ALLOWED_URL)
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
        call("/rest/api/2/repository/state", provisioner, ALLOWED_URL)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("MISSING"))
            .andExpect(jsonPath("$.hasCommits").value(false))
    }

    @Test
    fun `the state read answers 500 OTHER and no status when the provider fails`() {
        val client = serveAllowedGroup()
        `when`(client.getRepository("team-a", "repo")).thenThrow(IllegalStateException("provider down"))
        call("/rest/api/2/repository/state", provisioner, ALLOWED_URL)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
            .andExpect(jsonPath("$.status").doesNotExist())
    }

    @Test
    fun `the state read answers 500 OTHER and not MISSING when the history disappears after the repository was found`() {
        val client = serveAllowedGroup()
        `when`(client.getRepository("team-a", "repo")).thenReturn(
            GiteaRepository(
                id = 1,
                name = "repo",
                fullName = "team-a/repo",
                avatarUrl = "",
                description = "m-1",
                defaultBranch = "main",
                empty = false,
            ),
        )
        `when`(client.getCommits(anyString(), anyString(), anyMap())).thenThrow(GiteaNotFoundException("gone"))
        call("/rest/api/2/repository/state", provisioner, ALLOWED_URL)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
            .andExpect(jsonPath("$.status").doesNotExist())
    }

    @Test
    fun `the group check answers exists true for a group the provider finds`() {
        serveAllowedGroup().also { `when`(it.getOrganization("team-a")).thenReturn(mock(GiteaOrganization::class.java)) }
        call("/rest/api/2/groups", provisioner, ALLOWED_URL)
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.exists").value(true))
    }

    @Test
    fun `the group check answers exists false when the provider does not find the group`() {
        serveAllowedGroup().also { `when`(it.getOrganization("team-a")).thenThrow(GiteaNotFoundException("no such organisation")) }
        call("/rest/api/2/groups", provisioner, ALLOWED_URL)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exists").value(false))
    }

    @Test
    fun `the group check answers 500 and never exists false when the provider fails`() {
        serveAllowedGroup().also { `when`(it.getOrganization("team-a")).thenThrow(IllegalStateException("provider down")) }
        call("/rest/api/2/groups", provisioner, ALLOWED_URL)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
            .andExpect(jsonPath("$.exists").doesNotExist())
    }

    @Test
    fun `a group outside the allow-list answers 403 TARGET_NOT_ALLOWED on both reads and calls no VCS`() {
        val client = serveAllowedGroup()
        provisioningPaths.forEach { path ->
            call(path, provisioner, "ssh://git@gitea.example.org/other/repo.git")
                .andExpect(status().isForbidden)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
        }
        verifyNoInteractions(client)
    }

    @Test
    fun `a VCS without a provisioning credential answers 403 TARGET_NOT_ALLOWED on both reads and calls no VCS`() {
        val client = mock(GiteaClient::class.java)
        val service = GiteaService(serviceProperties(VcsServiceType.GITEA, null), client)
        `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenReturn(service)
        provisioningPaths.forEach { path ->
            call(path, provisioner, ALLOWED_URL)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_ALLOWED"))
        }
        verifyNoInteractions(client)
    }

    @Test
    fun `an sshUrl no VCS supports answers 400 INVALID_REQUEST on both reads`() {
        `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenThrow(IllegalStateException("There is no configured VCS service"))
        provisioningPaths.forEach { path ->
            call(path, provisioner, "ssh://git@elsewhere/team-a/repo.git")
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test
    fun `a missing sshUrl answers 400 INVALID_REQUEST on both reads`() {
        provisioningPaths.forEach { path ->
            mockMvc
                .perform(get(path).accept(MediaType.APPLICATION_JSON).with(provisioner))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test
    fun `the permission is checked before the target`() {
        serveAllowedGroup()
        call("/rest/api/2/groups", stranger, "ssh://git@gitea.example.org/other/repo.git")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("OTHER"))
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
        private const val SSH_URL = "ssh://git@host/team-a/repo.git"
        private const val ALLOWED_URL = "ssh://git@gitea.example.org/team-a/repo.git"
    }
}
