package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.Ref
import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.document.CommitDocument
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
    fun saveRepositoriesInfo(repositoriesInfo: List<RepositoryInfoDocument>)
    fun deleteRepositoryInfoById(repositoryId: String)
    fun findRefsByRepositoryId(repositoryId: String): Set<RefDocument>
    fun saveRefs(refs: List<RefDocument>)
    fun deleteRefsByIds(refsIds: List<String>)
    fun deleteRefsByRepositoryId(repositoryId: String)
    fun findCommitsByRepositoryId(repositoryId: String): Set<CommitDocument>
    fun saveCommits(commits: List<CommitDocument>)
    fun deleteCommitsByIds(commitsIds: List<String>)
    fun deleteCommitsByRepositoryId(repositoryId: String)
    fun findPullRequestsByRepositoryId(repositoryId: String): Set<PullRequestDocument>
    fun savePullRequests(pullRequests: List<PullRequestDocument>)
    fun deletePullRequestsByIds(pullRequestsIds: List<String>)
    fun deletePullRequestsByRepositoryId(repositoryId: String)
    fun findBranchesByIssueKey(issueKey: String): List<Branch>
    fun findCommitsByIssueKey(issueKey: String): List<Commit>
    fun findPullRequestsByIssueKey(issueKey: String): List<PullRequest>
    fun findByIssueKey(issueKey: String): SearchSummary

    companion object {
        fun Ref.toDocument(repositoryDocument: RepositoryDocument) =
            RefDocument(repositoryDocument, type, name, hash, link)

        fun RefDocument.toDto() = when (type) {
            RefType.BRANCH -> Branch(name, hash, link, repository.toDto())
            RefType.TAG -> Tag(name, hash, link, repository.toDto())
        }

        fun Commit.toDocument(repositoryDocument: RepositoryDocument) = CommitDocument(
            repositoryDocument, hash, message, date, author.toDocument(), parents, link
        )

        fun CommitDocument.toDto() =
            Commit(hash, message, date, author.toDto(), parents, link, repository.toDto())

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

        fun RepositoryDocument.toDto() = Repository(sshUrl, link, avatar)
    }
}