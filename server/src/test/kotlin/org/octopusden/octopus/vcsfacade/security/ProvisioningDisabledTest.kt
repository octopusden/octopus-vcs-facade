package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.octopusden.cloud.commons.security.client.AuthServerClient
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.config.ProvisioningConfig
import org.octopusden.octopus.vcsfacade.controller.ProvisioningController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Default settings: no `vcs-facade.provisioning.*` and no `auth-server.*`.
 */
@WebMvcTest
@ContextConfiguration(classes = [ExistingEndpointsTest.WebTestConfig::class])
class ProvisioningDisabledTest : ExistingEndpointsTest() {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `the context starts without auth server settings and without provisioning beans`() {
        assertTrue(context.getBeanNamesForType(ProvisioningController::class.java).isEmpty())
        assertTrue(context.getBeanNamesForType(WebSecurityConfig::class.java).isEmpty())
        assertTrue(context.getBeanNamesForType(AuthServerClient::class.java).isEmpty())
        // The upload limits and the lazy resolver are provisioning's own; off, Spring Boot's stay.
        assertTrue(context.getBeanNamesForType(ProvisioningConfig::class.java).isEmpty())
    }

    @Test
    fun `an existing GET carries no Spring Security response headers`() {
        `when`(vcsManager.getRepository(anyString())).thenReturn(Repository(SSH_URL, "http://host/g/r"))
        val response = mvc.perform(get("/rest/api/2/repository").param("sshUrl", SSH_URL)).andReturn().response
        listOf("Cache-Control", "Pragma", "Expires", "X-Content-Type-Options", "X-Frame-Options", "X-XSS-Protection")
            .forEach { assertFalse(response.containsHeader(it), "$it is set: ${response.getHeaders(it)}") }
    }

    @Test
    fun `the group check is not there`() {
        mvc.perform(get("/rest/api/2/groups").param("sshUrl", SSH_URL)).andExpect(status().isNotFound)
    }

    @Test
    fun `the state read is not there`() {
        mvc.perform(get("/rest/api/2/repository/state").param("sshUrl", SSH_URL)).andExpect(status().isNotFound)
    }

    @Test
    fun `the create is not there, and a POST answers 405 because the path has a GET`() {
        mvc.perform(post("/rest/api/2/repository").param("sshUrl", SSH_URL)).andExpect(status().isMethodNotAllowed)
    }

    companion object {
        private const val SSH_URL = "ssh://git@host/g/r.git"
    }
}
