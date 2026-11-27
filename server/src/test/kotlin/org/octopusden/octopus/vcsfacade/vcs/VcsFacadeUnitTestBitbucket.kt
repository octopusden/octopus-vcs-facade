package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCreateRepository
import org.octopusden.octopus.vcsfacade.TestService
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'bitbucket'] == 'bitbucket'}", loadContext = true)
class VcsFacadeUnitTestBitbucket :
    BaseVcsFacadeUnitTest(
        TestService.Bitbucket(vcsFacadeHost, vcsHost),
        BitbucketTestClient("http://$vcsHost", BITBUCKET_USER, BITBUCKET_PASSWORD),
    ) {
    // The state read against the real Bitbucket of the test setup, with the provisioning credential and no security
    // in between: an empty repository is where a real API differs from mocks.
    private val provisioningService by lazy {
        BitbucketService(
            VcsProperties.Service(
                id = "provisioning-test",
                type = VcsServiceType.BITBUCKET,
                httpUrl = "http://$vcsHost/",
                sshUrl = "ssh://git@$vcsHost/",
                token = null,
                username = BITBUCKET_USER,
                password = BITBUCKET_PASSWORD,
                healthCheck = null,
                provisioning = VcsProperties.Service.Provisioning(
                    username = BITBUCKET_USER,
                    password = BITBUCKET_PASSWORD,
                    allowedGroups = setOf(GROUP),
                ),
            ),
        )
    }

    @Test
    fun `provisioning state of an empty repository is present without commits`() {
        val repository = "provisioning-empty-repository"
        val client = checkNotNull(provisioningService.provisioningClient)
        // created through the provider and not imported, so nothing was pushed to it
        client.createRepository(GROUP, BitbucketCreateRepository(repository))
        try {
            assertEquals(
                RepositoryState(RepositoryStatus.PRESENT, null, null, hasCommits = false, rootCommitMarker = null),
                provisioningService.getRepositoryState(GROUP, repository),
            )
        } finally {
            client.deleteRepository(GROUP, repository)
        }
    }

    @Test
    fun `provisioning state of a repository with history`() {
        val state = provisioningService.getRepositoryState(GROUP, REPOSITORY_2)
        assertEquals(RepositoryStatus.PRESENT, state.status)
        assertTrue(state.hasCommits)
        // imported history, written without a marker
        assertNull(state.rootCommitMarker)
    }

    @Test
    fun `provisioning state of a repository that is not there is missing`() {
        assertEquals(RepositoryState(RepositoryStatus.MISSING), provisioningService.getRepositoryState(GROUP, "no-such-repository"))
    }

    @Test
    fun `provisioning state under a project that is not there is missing`() {
        assertEquals(RepositoryState(RepositoryStatus.MISSING), provisioningService.getRepositoryState("no-such-project", "repository"))
    }

    @Test
    fun `provisioning group check against the real provider`() {
        assertTrue(provisioningService.groupExists(GROUP))
        assertFalse(provisioningService.groupExists("no-such-project"))
    }
}
