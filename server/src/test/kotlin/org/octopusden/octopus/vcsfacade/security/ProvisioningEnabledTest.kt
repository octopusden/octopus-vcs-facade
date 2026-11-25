package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaOrganization
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.octopusden.octopus.vcsfacade.service.serviceProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
    fun `a token with the permission and an allowed target reaches the state read, which answers 501 for now`() {
        serveAllowedGroup()
        call("/rest/api/2/repository/state", provisioner, ALLOWED_URL)
            .andExpect(status().isNotImplemented)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorMessage").isNotEmpty)
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
