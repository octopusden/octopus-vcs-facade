package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.TestService
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'gitea'] == 'gitea'}", loadContext = true)
class VcsFacadeUnitTestGitea :
    BaseVcsFacadeUnitTest(
        TestService.Gitea(vcsFacadeHost, vcsHost),
        GiteaTestClient("http://$vcsHost", GITEA_USER, GITEA_PASSWORD),
    ) {
    // The state read against the real Gitea of the test setup, with the provisioning credential and no security
    // in between: the root walk is where a real API differs from mocks.
    private val provisioningService by lazy {
        GiteaService(
            VcsProperties.Service(
                id = "provisioning-test",
                type = VcsServiceType.GITEA,
                httpUrl = "http://$vcsHost/",
                sshUrl = "ssh://git@$vcsHost/",
                token = null,
                username = GITEA_USER,
                password = GITEA_PASSWORD,
                healthCheck = null,
                provisioning = VcsProperties.Service.Provisioning(
                    username = GITEA_USER,
                    password = GITEA_PASSWORD,
                    allowedGroups = setOf(GROUP),
                ),
            ),
        )
    }

    @BeforeAll
    fun beforeAllVcsFacadeUnitTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
    }

    @Test
    fun `provisioning state of a repository with history`() {
        val state = provisioningService.getRepositoryState(GROUP, REPOSITORY_2)
        assertEquals(RepositoryStatus.PRESENT, state.status)
        assertTrue(state.hasCommits)
        assertNotNull(state.defaultBranch)
        // imported history, written without a marker
        assertNull(state.rootCommitMarker)
    }

    @Test
    fun `provisioning state of a repository that is not there is missing`() {
        assertEquals(RepositoryState(RepositoryStatus.MISSING), provisioningService.getRepositoryState(GROUP, "no-such-repository"))
    }

    @Test
    fun `provisioning state under an organisation that is not there is missing`() {
        assertEquals(RepositoryState(RepositoryStatus.MISSING), provisioningService.getRepositoryState("no-such-org", "repository"))
    }

    @Test
    fun `provisioning group check against the real provider`() {
        assertTrue(provisioningService.groupExists(GROUP))
        assertFalse(provisioningService.groupExists("no-such-org"))
    }
}
