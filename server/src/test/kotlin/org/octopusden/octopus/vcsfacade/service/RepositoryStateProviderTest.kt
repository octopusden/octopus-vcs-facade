package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketBranch
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketEntityList
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketParent
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketRepository
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaEntityList
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaShortCommit
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus
import org.octopusden.octopus.vcsfacade.config.VcsProperties.Service.Provisioning
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.impl.BitbucketService
import org.octopusden.octopus.vcsfacade.service.impl.GiteaService
import java.io.IOException
import java.util.Date
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException as BitbucketNotFoundException
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException as GiteaNotFoundException

class RepositoryStateProviderTest {
    private val provisioning = Provisioning(token = "t", allowedGroups = setOf("g"))

    private val rootMessage = "Initial content\n\nProvisioning-Marker: m-1"

    private val bitbucketClient = mock(BitbucketClient::class.java)
    private val bitbucket = BitbucketService(serviceProperties(VcsServiceType.BITBUCKET, provisioning), bitbucketClient)

    // Gitea

    private val giteaClient = mock(GiteaClient::class.java)
    private val gitea = GiteaService(serviceProperties(VcsServiceType.GITEA, provisioning), giteaClient)

    private fun giteaRepository(
        description: String? = "m-1",
        defaultBranch: String? = "main",
        empty: Boolean? = false,
    ) = GiteaRepository(
        id = 1,
        name = "repo",
        fullName = "team/repo",
        avatarUrl = "",
        description = description,
        defaultBranch = defaultBranch,
        empty = empty,
    )

    private fun giteaCommit(
        sha: String,
        message: String,
        vararg parents: String,
    ) = GiteaCommit(
        sha,
        Date(0),
        GiteaCommit.GiteaRepoCommit(message, GiteaCommit.GiteaRepoCommitUser("a")),
        parents.map {
            GiteaShortCommit(it)
        },
        null,
        null,
    )

    private fun giteaPages(vararg pages: List<GiteaCommit>) {
        `when`(giteaClient.getCommits(anyString(), anyString(), anyMap())).thenAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(2)
            val index = (params["page"] as Int) - 1
            GiteaEntityList(index < pages.size - 1, pages.getOrElse(index) { emptyList() })
        }
    }

    @Test
    fun `gitea repository the provider does not find is missing`() {
        `when`(giteaClient.getRepository("team", "repo")).thenThrow(GiteaNotFoundException("no such repository"))
        assertEquals(RepositoryState(RepositoryStatus.MISSING), gitea.getRepositoryState("team", "repo"))
    }

    @Test
    fun `gitea organisation that is missing makes the repository missing`() {
        // the provider answers 404 for a repository under an organisation that does not exist
        `when`(giteaClient.getRepository("nobody", "repo")).thenThrow(GiteaNotFoundException("no such organisation"))
        assertEquals(RepositoryStatus.MISSING, gitea.getRepositoryState("nobody", "repo").status)
    }

    @Test
    fun `gitea empty repository is present without commits`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository(empty = true))
        assertEquals(
            RepositoryState(RepositoryStatus.PRESENT, "m-1", "main", hasCommits = false, rootCommitMarker = null),
            gitea.getRepositoryState("team", "repo"),
        )
    }

    @Test
    fun `gitea repository with commits and a root trailer`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository())
        giteaPages(listOf(giteaCommit("b", "second", "a"), giteaCommit("a", rootMessage)))
        assertEquals(
            RepositoryState(RepositoryStatus.PRESENT, "m-1", "main", hasCommits = true, rootCommitMarker = "m-1"),
            gitea.getRepositoryState("team", "repo"),
        )
    }

    @Test
    fun `gitea repository with commits but no trailer`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository(description = "someone else"))
        giteaPages(listOf(giteaCommit("a", "Initial content")))
        assertEquals(
            RepositoryState(RepositoryStatus.PRESENT, "someone else", "main", hasCommits = true, rootCommitMarker = null),
            gitea.getRepositoryState("team", "repo"),
        )
    }

    @Test
    fun `gitea walks the default branch across pages`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository())
        giteaPages(
            listOf(giteaCommit("c", "third", "b")),
            listOf(giteaCommit("b", "second", "a")),
            listOf(giteaCommit("a", rootMessage)),
        )
        assertEquals("m-1", gitea.getRepositoryState("team", "repo").rootCommitMarker)
    }

    @Test
    fun `gitea empty description is no marker`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository(description = "", empty = true))
        assertEquals(null, gitea.getRepositoryState("team", "repo").marker)
    }

    @Test
    fun `gitea failure other than not found propagates`() {
        doThrow(IllegalStateException("500")).`when`(giteaClient).getRepository("team", "repo")
        assertThrows(IllegalStateException::class.java) { gitea.getRepositoryState("team", "repo") }
        doThrow(RuntimeException(IOException("down"))).`when`(giteaClient).getRepository("team", "repo")
        assertThrows(RuntimeException::class.java) { gitea.getRepositoryState("team", "repo") }
    }

    @Test
    fun `gitea failure while reading the history propagates`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository())
        `when`(giteaClient.getCommits(anyString(), anyString(), anyMap())).thenThrow(IllegalStateException("500"))
        assertThrows(IllegalStateException::class.java) { gitea.getRepositoryState("team", "repo") }
    }

    @Test
    fun `gitea not found while reading the history is a failed read and not a missing repository`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository())
        `when`(giteaClient.getCommits(anyString(), anyString(), anyMap())).thenThrow(GiteaNotFoundException("gone"))
        assertFailedRead { gitea.getRepositoryState("team", "repo") }
    }

    @Test
    fun `gitea not found on a later page is a failed read`() {
        `when`(giteaClient.getRepository("team", "repo")).thenReturn(giteaRepository())
        `when`(giteaClient.getCommits(anyString(), anyString(), anyMap())).thenAnswer { invocation ->
            if (invocation.getArgument<Map<String, Any>>(2)["page"] == 1) {
                GiteaEntityList(true, listOf(giteaCommit("b", "second", "a")))
            } else {
                throw GiteaNotFoundException("gone")
            }
        }
        assertFailedRead { gitea.getRepositoryState("team", "repo") }
    }

    private fun assertFailedRead(call: () -> Any?) {
        val e = assertThrows(IllegalStateException::class.java) { call() }
        assertTrue(e.cause is GiteaNotFoundException || e.cause is BitbucketNotFoundException, "cause: ${e.cause}")
    }

    // Bitbucket

    // The mock is made first: stubbing inside the argument of another stubbing is rejected by Mockito.
    private fun givenBitbucketRepository(description: String? = "m-1") {
        val repository = mock(BitbucketRepository::class.java)
        `when`(repository.description).thenReturn(description)
        `when`(bitbucketClient.getRepository("proj", "repo")).thenReturn(repository)
    }

    private fun bitbucketCommit(
        id: String,
        message: String,
        vararg parents: String,
    ) = mock(BitbucketCommit::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.message).thenReturn(message)
        `when`(it.parents).thenReturn(parents.map { parent -> BitbucketParent(parent) })
    }

    private fun bitbucketPages(vararg pages: List<BitbucketCommit>) {
        `when`(bitbucketClient.getCommits(anyString(), anyString(), anyMap())).thenAnswer { invocation ->
            val params = invocation.getArgument<Map<String, Any>>(2)
            val index = params["start"] as Int
            BitbucketEntityList(
                pages[index],
                pages[index].size,
                index == pages.size - 1,
                index,
                100,
                if (index ==
                    pages.size - 1
                ) {
                    null
                } else {
                    index + 1
                },
            )
        }
    }

    private fun bitbucketBranches(vararg branches: BitbucketBranch) {
        `when`(bitbucketClient.getBranches(anyString(), anyString(), anyMap()))
            .thenReturn(BitbucketEntityList(branches.toList(), branches.size, true, 0, 100, null))
    }

    private fun branch(
        name: String,
        head: String,
    ) = BitbucketBranch("refs/heads/$name", name, head)

    @Test
    fun `bitbucket repository the provider does not find is missing`() {
        `when`(bitbucketClient.getRepository("proj", "repo")).thenThrow(BitbucketNotFoundException("no such repository"))
        assertEquals(RepositoryState(RepositoryStatus.MISSING), bitbucket.getRepositoryState("proj", "repo"))
    }

    @Test
    fun `bitbucket project that is missing makes the repository missing`() {
        `when`(bitbucketClient.getRepository("nobody", "repo")).thenThrow(BitbucketNotFoundException("no such project"))
        assertEquals(RepositoryStatus.MISSING, bitbucket.getRepositoryState("nobody", "repo").status)
    }

    @Test
    fun `bitbucket empty repository answers 404 for its commits and is present without commits`() {
        givenBitbucketRepository()
        `when`(bitbucketClient.getCommits(anyString(), anyString(), anyMap())).thenThrow(BitbucketNotFoundException("no commits"))
        bitbucketBranches()
        assertEquals(
            RepositoryState(RepositoryStatus.PRESENT, "m-1", null, hasCommits = false, rootCommitMarker = null),
            bitbucket.getRepositoryState("proj", "repo"),
        )
    }

    @Test
    fun `bitbucket empty repository answering an empty list is present without commits`() {
        givenBitbucketRepository()
        bitbucketPages(emptyList())
        bitbucketBranches()
        assertEquals(false, bitbucket.getRepositoryState("proj", "repo").hasCommits)
    }

    @Test
    fun `bitbucket repository with commits and a root trailer`() {
        givenBitbucketRepository()
        bitbucketPages(listOf(bitbucketCommit("b", "second", "a"), bitbucketCommit("a", rootMessage)))
        bitbucketBranches(branch("main", "b"))
        assertEquals(
            RepositoryState(RepositoryStatus.PRESENT, "m-1", "main", hasCommits = true, rootCommitMarker = "m-1"),
            bitbucket.getRepositoryState("proj", "repo"),
        )
    }

    @Test
    fun `bitbucket repository with commits but no trailer`() {
        givenBitbucketRepository(description = null)
        bitbucketPages(listOf(bitbucketCommit("a", "Initial content")))
        bitbucketBranches(branch("main", "a"))
        assertEquals(
            RepositoryState(RepositoryStatus.PRESENT, null, "main", hasCommits = true, rootCommitMarker = null),
            bitbucket.getRepositoryState("proj", "repo"),
        )
    }

    @Test
    fun `bitbucket walks the default branch across pages`() {
        givenBitbucketRepository()
        bitbucketPages(
            listOf(bitbucketCommit("c", "third", "b")),
            listOf(bitbucketCommit("b", "second", "a")),
            listOf(bitbucketCommit("a", rootMessage)),
        )
        bitbucketBranches(branch("main", "c"))
        assertEquals("m-1", bitbucket.getRepositoryState("proj", "repo").rootCommitMarker)
    }

    @Test
    fun `bitbucket default branch is only named when exactly one branch points at the head`() {
        givenBitbucketRepository()
        bitbucketPages(listOf(bitbucketCommit("a", rootMessage)))
        bitbucketBranches(branch("main", "a"), branch("feature", "a"))
        assertEquals(null, bitbucket.getRepositoryState("proj", "repo").defaultBranch)
        bitbucketBranches(branch("main", "a"), branch("feature", "zzz"))
        assertEquals("main", bitbucket.getRepositoryState("proj", "repo").defaultBranch)
    }

    @Test
    fun `bitbucket failure other than not found propagates`() {
        doThrow(IllegalStateException("500")).`when`(bitbucketClient).getRepository("proj", "repo")
        assertThrows(IllegalStateException::class.java) { bitbucket.getRepositoryState("proj", "repo") }
        doThrow(RuntimeException(IOException("down"))).`when`(bitbucketClient).getRepository("proj", "repo")
        assertThrows(RuntimeException::class.java) { bitbucket.getRepositoryState("proj", "repo") }
    }

    @Test
    fun `bitbucket failure while reading the history propagates`() {
        givenBitbucketRepository()
        `when`(bitbucketClient.getCommits(anyString(), anyString(), anyMap())).thenThrow(IllegalStateException("500"))
        assertThrows(IllegalStateException::class.java) { bitbucket.getRepositoryState("proj", "repo") }
    }

    @Test
    fun `bitbucket not found on a later commits page is a failed read and not a missing repository`() {
        givenBitbucketRepository()
        bitbucketBranches(branch("main", "c"))
        `when`(bitbucketClient.getCommits(anyString(), anyString(), anyMap())).thenAnswer { invocation ->
            val start = invocation.getArgument<Map<String, Any>>(2)["start"] as Int
            if (start == 0) {
                BitbucketEntityList(listOf(bitbucketCommit("c", "third", "b")), 1, false, 0, 100, 1)
            } else {
                throw BitbucketNotFoundException("gone")
            }
        }
        assertFailedRead { bitbucket.getRepositoryState("proj", "repo") }
    }

    @Test
    fun `bitbucket not found while reading the branches is a failed read`() {
        givenBitbucketRepository()
        bitbucketPages(listOf(bitbucketCommit("a", rootMessage)))
        `when`(bitbucketClient.getBranches(anyString(), anyString(), anyMap())).thenThrow(BitbucketNotFoundException("gone"))
        assertFailedRead { bitbucket.getRepositoryState("proj", "repo") }
    }

    @Test
    fun `bitbucket not found on the first commits page of a vanished repository is a failed read`() {
        givenBitbucketRepository()
        `when`(bitbucketClient.getCommits(anyString(), anyString(), anyMap())).thenThrow(BitbucketNotFoundException("gone"))
        `when`(bitbucketClient.getBranches(anyString(), anyString(), anyMap())).thenThrow(BitbucketNotFoundException("gone"))
        assertFailedRead { bitbucket.getRepositoryState("proj", "repo") }
    }

    @Test
    fun `bitbucket not found on the first commits page while branches exist is a failed read`() {
        givenBitbucketRepository()
        `when`(bitbucketClient.getCommits(anyString(), anyString(), anyMap())).thenThrow(BitbucketNotFoundException("gone"))
        bitbucketBranches(branch("main", "a"))
        assertThrows(IllegalStateException::class.java) { bitbucket.getRepositoryState("proj", "repo") }
    }

    @Test
    fun `bitbucket empty first commits page while branches exist is a failed read`() {
        givenBitbucketRepository()
        bitbucketPages(emptyList())
        bitbucketBranches(branch("main", "a"))
        assertThrows(IllegalStateException::class.java) { bitbucket.getRepositoryState("proj", "repo") }
    }
}
