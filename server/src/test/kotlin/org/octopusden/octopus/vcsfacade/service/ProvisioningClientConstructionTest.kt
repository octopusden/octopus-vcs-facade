package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService

class ProvisioningClientConstructionTest {
    private fun gitea(provisioning: Provisioning?) = createService(VcsServiceType.GITEA, provisioning) as GiteaService

    private fun bitbucket(provisioning: Provisioning?) = createService(VcsServiceType.BITBUCKET, provisioning) as BitbucketService

    @Test
    fun `no provisioning client without a provisioning block`() {
        assertNull(gitea(null).provisioningClient)
        assertNull(bitbucket(null).provisioningClient)
    }

    @Test
    fun `no provisioning client without a credential`() {
        val onlyGroups = Provisioning(allowedGroups = setOf("g"))
        assertNull(gitea(onlyGroups).provisioningClient)
        assertNull(bitbucket(onlyGroups).provisioningClient)
        val halfBasic = Provisioning(username = "u")
        assertNull(gitea(halfBasic).provisioningClient)
        assertNull(bitbucket(halfBasic).provisioningClient)
    }

    @Test
    fun `a provisioning client is built from a token`() {
        assertNotNull(gitea(Provisioning(token = "t")).provisioningClient)
        assertNotNull(bitbucket(Provisioning(token = "t")).provisioningClient)
    }

    @Test
    fun `a provisioning client is built from a username and password`() {
        assertNotNull(gitea(Provisioning(username = "u", password = "p")).provisioningClient)
        assertNotNull(bitbucket(Provisioning(username = "u", password = "p")).provisioningClient)
    }

    @Test
    fun `an injected provisioning client is used instead of building one`() {
        val provisioning = Provisioning(token = "t")
        val giteaClient = mock(GiteaClient::class.java)
        val bitbucketClient = mock(BitbucketClient::class.java)
        assertSame(giteaClient, GiteaService(serviceProperties(VcsServiceType.GITEA, provisioning), giteaClient).provisioningClient)
        assertSame(
            bitbucketClient,
            BitbucketService(serviceProperties(VcsServiceType.BITBUCKET, provisioning), bitbucketClient).provisioningClient,
        )
    }
}
