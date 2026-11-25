package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Test
import org.octopusden.cloud.commons.security.client.AuthServerClient
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
    ): ResultActions =
        mockMvc.perform(
            get(path)
                .param("sshUrl", SSH_URL)
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
    fun `a token with the permission reaches the controller, which answers 501 for now`() {
        provisioningPaths.forEach { path ->
            call(path, provisioner)
                .andExpect(status().isNotImplemented)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty)
        }
    }

    companion object {
        private const val SSH_URL = "ssh://git@host/team-a/repo.git"
    }
}
