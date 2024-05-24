package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.FileChange
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.Ref
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.document.CommitDocument
import org.octopusden.octopus.vcsfacade.document.FileChangeDocument
import org.octopusden.octopus.vcsfacade.document.PullRequestDocument
import org.octopusden.octopus.vcsfacade.document.PullRequestReviewerDocument
import org.octopusden.octopus.vcsfacade.document.RefDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryDocument
import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.octopusden.octopus.vcsfacade.document.UserDocument
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType


interface OpenSearchService {
    fun findRepositoriesInfoByRepositoryType(type: VcsServiceType): Set<RepositoryInfoDocument>
    fun findRepositoryInfoById(repositoryId: String): RepositoryInfoDocument?
    fun saveRepositoriesInfo(repositoriesInfo: Sequence<RepositoryInfoDocument>)
    fun deleteRepositoryInfoById(repositoryId: String)
    fun findRefsIdsByRepositoryId(repositoryId: String): Set<String>
    fun saveRefs(refs: Sequence<RefDocument>)
    fun deleteRefsByIds(refsIds: Sequence<String>)
    fun deleteRefsByRepositoryId(repositoryId: String)
    fun findCommitsIdsByRepositoryId(repositoryId: String): Set<String>
    fun saveCommits(commits: Sequence<CommitDocument>)
    fun deleteCommitsByIds(commitsIds: Sequence<String>)
    fun deleteCommitsByRepositoryId(repositoryId: String)
    fun findPullRequestsIdsByRepositoryId(repositoryId: String): Set<String>
    fun savePullRequests(pullRequests: Sequence<PullRequestDocument>)
    fun deletePullRequestsByIds(pullRequestsIds: Sequence<String>)
    fun deletePullRequestsByRepositoryId(repositoryId: String)
    fun findBranchesByIssueKey(issueKey: String): Sequence<RefDocument>
    fun findCommitsByIssueKey(issueKey: String): Sequence<CommitDocument>
    fun findPullRequestsByIssueKey(issueKey: String): Sequence<PullRequestDocument>
    fun findByIssueKey(issueKey: String): SearchSummary

    companion object {
        fun Ref.toDocument(repositoryDocument: RepositoryDocument) =
            RefDocument(repositoryDocument, type, name, hash, link)

        fun RefDocument.toDto() = when (type) {
            RefType.BRANCH -> Branch(name, hash, link, repository.toDto())
            RefType.TAG -> Tag(name, hash, link, repository.toDto())
        }

        fun CommitWithFiles.toDocument(repositoryDocument: RepositoryDocument) = CommitDocument(
            repositoryDocument,
            commit.hash,
            commit.message,
            commit.date,
            commit.author.toDocument(),
            commit.parents,
            commit.link,
            files.map { it.toDocument() }
        )

        fun CommitDocument.toDto() = CommitWithFiles(
            Commit(hash, message, date, author.toDto(), parents, link, repository.toDto()),
            files.size,
            files.map { it.toDto() }
        )

        fun PullRequest.toDocument(repositoryDocument: RepositoryDocument) = PullRequestDocument(
            repositoryDocument,
            index,
            title,
            description,
            author.toDocument(),
            source,
            target,
            assignees.map { it.toDocument() },
            reviewers.map { it.toDocument() },
            status,
            createdAt,
            updatedAt,
            link
        )

        fun PullRequestDocument.toDto() = PullRequest(
            index,
            title,
            description,
            author.toDto(),
            source,
            target,
            assignees.map { it.toDto() },
            reviewers.map { it.toDto() },
            status,
            createdAt,
            updatedAt,
            link,
            repository.toDto()
        )

        private fun PullRequestReviewer.toDocument() = PullRequestReviewerDocument(user.toDocument(), approved)

        private fun PullRequestReviewerDocument.toDto() = PullRequestReviewer(user.toDto(), approved)

        private fun User.toDocument() = UserDocument(name, avatar)

        private fun UserDocument.toDto() = User(name, avatar)

        private fun FileChange.toDocument() = FileChangeDocument(type, path, link)

        private fun FileChangeDocument.toDto() = FileChange(type, path, link)

        fun RepositoryDocument.toDto() = Repository(sshUrl, link, avatar)
    }
}