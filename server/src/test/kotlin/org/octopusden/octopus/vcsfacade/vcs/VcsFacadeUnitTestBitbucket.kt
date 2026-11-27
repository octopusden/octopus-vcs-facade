package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
        // unique per run, so a repository left behind by an earlier run cannot make the create fail
        val repository = "provisioning-empty-${System.currentTimeMillis().toString(36)}"
        val client = checkNotNull(provisioningService.provisioningClient)
        try {
            // created through the provider and not imported, so nothing was pushed to it
            client.createRepository(GROUP, BitbucketCreateRepository(repository))
            assertEquals(
                RepositoryState(RepositoryStatus.PRESENT, null, null, hasCommits = false, rootCommitMarker = null),
                provisioningService.getRepositoryState(GROUP, repository),
            )
        } finally {
            // a failed create leaves nothing to delete and must not mask the original error
            try {
                client.deleteRepository(GROUP, repository)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun `provisioning state of a repository with history`() {
        val state = provisioningService.getRepositoryState(GROUP, REPOSITORY_2)
        assertEquals(RepositoryStatus.PRESENT, state.status)
        assertTrue(state.hasCommits)
        // every head of the imported history is pointed at by exactly one branch, so the default branch is named
        // (which branch the provider makes the default after the import is not fixed by the test data)
        assertNotNull(state.defaultBranch)
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
