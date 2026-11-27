package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketProject
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaOrganization
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import java.io.IOException
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException as BitbucketNotFoundException
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException as GiteaNotFoundException

class GroupExistsTest {
    private val provisioning = Provisioning(token = "t", allowedGroups = setOf("g"))

    private val bitbucketClient = mock(BitbucketClient::class.java)
    private val bitbucket = BitbucketService(serviceProperties(VcsServiceType.BITBUCKET, provisioning), bitbucketClient)

    private val giteaClient = mock(GiteaClient::class.java)
    private val gitea = GiteaService(serviceProperties(VcsServiceType.GITEA, provisioning), giteaClient)

    @Test
    fun `bitbucket project that exists`() {
        `when`(bitbucketClient.getProject("proj")).thenReturn(mock(BitbucketProject::class.java))
        assertTrue(bitbucket.groupExists("proj"))
        verify(bitbucketClient).getProject("proj")
    }

    @Test
    fun `bitbucket project the provider does not find`() {
        `when`(bitbucketClient.getProject("proj")).thenThrow(BitbucketNotFoundException("no such project"))
        assertFalse(bitbucket.groupExists("proj"))
    }

    @Test
    fun `bitbucket failure other than not found is not a missing project`() {
        `when`(bitbucketClient.getProject("proj")).thenThrow(IllegalStateException("500"))
        assertThrows(IllegalStateException::class.java) { bitbucket.groupExists("proj") }
        doThrow(RuntimeException(IOException("down"))).`when`(bitbucketClient).getProject("proj")
        assertThrows(RuntimeException::class.java) { bitbucket.groupExists("proj") }
    }

    @Test
    fun `gitea organisation that exists`() {
        `when`(giteaClient.getOrganization("team")).thenReturn(mock(GiteaOrganization::class.java))
        assertTrue(gitea.groupExists("team"))
        verify(giteaClient).getOrganization("team")
    }

    @Test
    fun `gitea organisation the provider does not find`() {
        `when`(giteaClient.getOrganization("team")).thenThrow(GiteaNotFoundException("no such organisation"))
        assertFalse(gitea.groupExists("team"))
    }

    @Test
    fun `gitea failure other than not found is not a missing organisation`() {
        `when`(giteaClient.getOrganization("team")).thenThrow(IllegalStateException("500"))
        assertThrows(IllegalStateException::class.java) { gitea.groupExists("team") }
        doThrow(RuntimeException(IOException("down"))).`when`(giteaClient).getOrganization("team")
        assertThrows(RuntimeException::class.java) { gitea.groupExists("team") }
    }
}
