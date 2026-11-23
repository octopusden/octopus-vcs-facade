package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.octopusden.octopus.vcsfacade.client.common.exception.TargetNotAllowedException
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType

class ProvisioningTargetResolverTest {
    private val vcsManager = mock(VcsManager::class.java)
    private val resolver = ProvisioningTargetResolver(vcsManager)

    private fun serve(
        type: VcsServiceType,
        provisioning: Provisioning?,
    ) = createService(type, provisioning).also { `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenReturn(it) }

    @Test
    fun `an allowed group resolves to the service, group and repository`() {
        val service = serve(VcsServiceType.GITEA, Provisioning(token = "t", allowedGroups = setOf("team-a")))
        val target = resolver.resolve("ssh://git@gitea.example.org/team-a/repo.git")
        assertSame(service, target.service)
        assertEquals("team-a", target.group)
        assertEquals("repo", target.repository)
    }

    @Test
    fun `groups compare without regard to case`() {
        serve(VcsServiceType.BITBUCKET, Provisioning(token = "t", allowedGroups = setOf("PROJ")))
        assertEquals("proj", resolver.resolve("ssh://git@bitbucket.example.org/proj/repo.git").group)
        assertEquals("proj", resolver.resolve("ssh://git@bitbucket.example.org/scm/PROJ/repo.git").group)
    }

    @Test
    fun `a group outside the allow-list is refused`() {
        serve(VcsServiceType.GITEA, Provisioning(token = "t", allowedGroups = setOf("team-a")))
        assertThrows(TargetNotAllowedException::class.java) { resolver.resolve("ssh://git@gitea.example.org/team-b/repo.git") }
    }

    @Test
    fun `an empty allow-list refuses everything`() {
        serve(VcsServiceType.GITEA, Provisioning(token = "t"))
        assertThrows(TargetNotAllowedException::class.java) { resolver.resolve("ssh://git@gitea.example.org/team-a/repo.git") }
    }

    @Test
    fun `a service without a provisioning block is refused`() {
        serve(VcsServiceType.GITEA, null)
        assertThrows(TargetNotAllowedException::class.java) { resolver.resolve("ssh://git@gitea.example.org/team-a/repo.git") }
    }

    @Test
    fun `a service with groups but no credential is refused`() {
        serve(VcsServiceType.GITEA, Provisioning(allowedGroups = setOf("team-a")))
        assertThrows(TargetNotAllowedException::class.java) { resolver.resolve("ssh://git@gitea.example.org/team-a/repo.git") }
    }

    @Test
    fun `a username without a password is no credential`() {
        serve(VcsServiceType.GITEA, Provisioning(username = "u", allowedGroups = setOf("team-a")))
        assertThrows(TargetNotAllowedException::class.java) { resolver.resolve("ssh://git@gitea.example.org/team-a/repo.git") }
    }

    @Test
    fun `an url no service supports fails as it does for every endpoint`() {
        `when`(vcsManager.getVcsServiceForSshUrl(anyString())).thenThrow(IllegalStateException("There is no configured VCS service"))
        assertThrows(IllegalStateException::class.java) { resolver.resolve("ssh://git@elsewhere/team-a/repo.git") }
    }
}
