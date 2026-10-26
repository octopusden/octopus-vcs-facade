package org.octopusden.octopus.vcsfacade.service.impl

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.octopusden.octopus.vcsfacade.service.VcsService
import org.springframework.core.task.AsyncTaskExecutor
import java.util.Date

class IndexerServiceImplTest {
    private lateinit var vcsManager: VcsManager
    private lateinit var openSearchService: OpenSearchService
    private lateinit var vcsService: VcsService
    private lateinit var indexerService: IndexerServiceImpl
    private lateinit var repositoryDocument: RepositoryDocument
    private lateinit var repository: Repository

    @BeforeEach
    fun setUp() {
        vcsManager = mock(VcsManager::class.java)
        openSearchService = mock(OpenSearchService::class.java)
        vcsService = mock(VcsService::class.java)
        indexerService = IndexerServiceImpl(
            vcsManager,
            openSearchService,
            mock(AsyncTaskExecutor::class.java),
            true,
        )
        repositoryDocument = RepositoryDocument(
            VCS_SERVICE_ID,
            GROUP,
            REPOSITORY,
            SSH_URL,
            LINK,
            null,
        )
        repository = Repository(SSH_URL, LINK)

        doReturn(vcsService).`when`(vcsManager).findVcsServiceById(VCS_SERVICE_ID)
        doReturn(VCS_SERVICE_ID).`when`(vcsService).id
        doReturn(true).`when`(vcsService).indexing
        doReturn(GROUP to REPOSITORY).`when`(vcsService).parse(SSH_URL)
        doReturn(repository).`when`(vcsService).findRepository(GROUP, REPOSITORY)
        doReturn(sequenceOf(Branch("main", "branch-hash", "branch-link", repository)))
            .`when`(vcsService)
            .getBranches(GROUP, REPOSITORY)
        doReturn(sequenceOf(commitWithFiles()))
            .`when`(vcsService)
            .getBranchesCommitGraph(GROUP, REPOSITORY)
        doReturn(emptySequence<PullRequest>()).`when`(vcsService).getPullRequests(GROUP, REPOSITORY)

        doReturn(setOf("branch-id")).`when`(openSearchService).saveRefs(anyArg())
        doReturn(setOf("old-commit")).`when`(openSearchService).findCommitsIdsByRepositoryId(repositoryDocument.id)
        doReturn(setOf("saved-commit")).`when`(openSearchService).saveCommits(anyArg())
        doReturn(emptySet<String>()).`when`(openSearchService).findPullRequestsIdsByRepositoryId(repositoryDocument.id)
        doReturn(emptySet<String>()).`when`(openSearchService).savePullRequests(anyArg())
    }

    @Test
    fun `scan continues when tags cannot be read`() {
        doReturn(setOf("old-branch", "old-tag"))
            .`when`(openSearchService)
            .findRefsIdsByRepositoryId(repositoryDocument.id)
        `when`(vcsService.getTags(GROUP, REPOSITORY)).thenThrow(IllegalStateException("tags failed"))

        indexerService.scan(repositoryDocument)

        verify(openSearchService, times(1)).saveRepositoriesInfo(anyArg())
        verify(openSearchService, times(1)).saveRefs(anyArg())
        verify(openSearchService, never()).deleteRefsByIds(anyArg())
        verify(openSearchService, times(1)).saveCommits(anyArg())
        verify(openSearchService, times(1)).savePullRequests(anyArg())
        verify(openSearchService, times(1)).deleteCommitsByIds(setOf("old-commit"))
    }

    @Test
    fun `scan removes stale refs when tags are read`() {
        doReturn(setOf("branch-id", "tag-id", "stale-id"))
            .`when`(openSearchService)
            .findRefsIdsByRepositoryId(repositoryDocument.id)
        doReturn(setOf("branch-id"))
            .doReturn(setOf("tag-id"))
            .`when`(openSearchService)
            .saveRefs(anyArg())
        doReturn(sequenceOf(Tag("v1", "tag-hash", "tag-link", repository)))
            .`when`(vcsService)
            .getTags(GROUP, REPOSITORY)

        indexerService.scan(repositoryDocument)

        verify(openSearchService, times(1)).deleteRefsByIds(setOf("stale-id"))
        verify(openSearchService, times(1)).saveCommits(anyArg())
        verify(openSearchService, times(1)).savePullRequests(anyArg())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyArg(): T = any<T>() as T

    private fun commitWithFiles() =
        CommitWithFiles(
            Commit(
                "commit-hash",
                "message",
                Date(0),
                User("author"),
                emptyList(),
                "commit-link",
                repository,
            ),
            0,
            emptyList(),
        )

    companion object {
        private const val VCS_SERVICE_ID = "gitea"
        private const val GROUP = "group"
        private const val REPOSITORY = "repository"
        private const val SSH_URL = "ssh://git@example.com/group/repository.git"
        private const val LINK = "https://example.com/group/repository"
    }
}
