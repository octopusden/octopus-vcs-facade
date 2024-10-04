package org.octopusden.octopus.vcsfacade.service.impl

import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Date
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClassicClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCreateTag
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaPullRequest
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaPullRequestReview
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaUser
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException
import org.octopusden.octopus.infrastructure.gitea.client.getBranches
import org.octopusden.octopus.infrastructure.gitea.client.getBranchesCommitGraph
import org.octopusden.octopus.infrastructure.gitea.client.getCommit
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import org.octopusden.octopus.infrastructure.gitea.client.getOrganizations
import org.octopusden.octopus.infrastructure.gitea.client.getPullRequestReviews
import org.octopusden.octopus.infrastructure.gitea.client.getPullRequests
import org.octopusden.octopus.infrastructure.gitea.client.getRepositories
import org.octopusden.octopus.infrastructure.gitea.client.getTags
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.FileChange
import org.octopusden.octopus.vcsfacade.client.common.dto.FileChangeType
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate.DateValue
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate.HashOrRefValue
import org.octopusden.octopus.vcsfacade.service.VcsService
import org.slf4j.LoggerFactory

class GiteaService(
    vcsServiceProperties: VcsProperties.Service
) : VcsService(vcsServiceProperties) {
    private val client: GiteaClient = GiteaClassicClient(object : ClientParametersProvider {
        override fun getApiUrl() = httpUrl
        override fun getAuth() = vcsServiceProperties.getCredentialProvider()
    })

    override fun getRepositories(): Sequence<Repository> {
        log.trace("=> getRepositories()")
        return client.getOrganizations().asSequence().flatMap { client.getRepositories(it.name) }
            .map { toRepository(it) }
            .also { if (log.isTraceEnabled) log.trace("<= getRepositories(): {}", it.toList()) }
    }

    private fun getRepository(group: String, repository: String) =
        toRepository(client.getRepository(group, repository))

    override fun findRepository(group: String, repository: String): Repository? {
        log.trace("=> findRepository({}, {})", group, repository)
        return try {
            getRepository(group, repository)
        } catch (e: NotFoundException) {
            null
        }.also { log.trace("<= findRepository({}, {}): {}", group, repository, it) }
    }

    override fun getBranches(group: String, repository: String): Sequence<Branch> {
        log.trace("=> getBranches({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getBranches(group, repository).asSequence().map { it.toBranch(this) }
        }.also { if (log.isTraceEnabled) log.trace("<= getBranches({}, {}): {}", group, repository, it.toList()) }
    }

    override fun getTags(group: String, repository: String): Sequence<Tag> {
        log.trace("=> getTags({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getTags(group, repository).asSequence().map { it.toTag(this) }
        }.also { if (log.isTraceEnabled) log.trace("<= getTags({}, {}): {}", group, repository, it.toList()) }
    }

    override fun createTag(group: String, repository: String, createTag: CreateTag): Tag {
        log.trace("=> createTag({}, {}, {})", group, repository, createTag)
        return with(getRepository(group, repository)) {
            client.createTag(
                group, repository, GiteaCreateTag(createTag.name, createTag.hashOrRef, createTag.message)
            ).toTag(this)
        }.also { log.trace("<= createTag({}, {}, {}): {}", group, repository, createTag, it) }
    }

    override fun getTag(group: String, repository: String, name: String): Tag {
        log.trace("=> getTag({}, {}, {})", group, repository, name)
        return with(getRepository(group, repository)) {
            client.getTag(group, repository, name).toTag(this)
        }.also { log.trace("<= getTag({}, {}, {}): {}", group, repository, name, it) }
    }

    override fun deleteTag(group: String, repository: String, name: String) {
        log.trace("=> deleteTag({}, {}, {})", group, repository, name)
        client.deleteTag(group, repository, name)
        log.trace("<= deleteTag({}, {}, {})", group, repository, name)
    }

    override fun getCommits(
        group: String, repository: String, from: HashOrRefOrDate<String, Date>?, toHashOrRef: String
    ): Sequence<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, from, toHashOrRef)
        return with(getRepository(group, repository)) {
            val commits = if (from is HashOrRefValue) {
                client.getCommits(group, repository, toHashOrRef, from.value)
            } else {
                client.getCommits(group, repository, toHashOrRef, (from as? DateValue)?.value)
            }
            commits.asSequence().map { it.toCommit(this) }
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= getCommits({}, {}, {}, {}): {}",
                group,
                repository,
                from,
                toHashOrRef,
                it.toList()
            )
        }
    }

    override fun getCommitsWithFiles(
        group: String, repository: String, from: HashOrRefOrDate<String, Date>?, toHashOrRef: String
    ): Sequence<CommitWithFiles> {
        log.trace("=> getCommitsWithFiles({}, {}, {}, {})", group, repository, from, toHashOrRef)
        return with(getRepository(group, repository)) {
            val commits = if (from is HashOrRefValue) {
                client.getCommits(group, repository, toHashOrRef, from.value, true)
            } else {
                client.getCommits(group, repository, toHashOrRef, (from as? DateValue)?.value, true)
            }
            commits.asSequence().map { it.toCommitWithFiles(this) }
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= getCommitsWithFiles({}, {}, {}, {}): {}",
                group,
                repository,
                from,
                toHashOrRef,
                it.toList()
            )
        }
    }

    override fun getBranchesCommitGraph(group: String, repository: String): Sequence<CommitWithFiles> {
        log.trace("=> getBranchesCommitGraph({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getBranchesCommitGraph(group, repository, true).asSequence().map { it.toCommitWithFiles(this) }
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= getBranchesCommitGraph({}, {}): {}",
                group,
                repository,
                it.toList()
            )
        }
    }

    override fun getCommit(group: String, repository: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {}, {})", group, repository, hashOrRef)
        return client.getCommit(group, repository, hashOrRef).toCommit(getRepository(group, repository))
            .also { log.trace("<= getCommit({}, {}, {}): {}", group, repository, hashOrRef, it) }
    }

    override fun getCommitWithFiles(group: String, repository: String, hashOrRef: String): CommitWithFiles {
        log.trace("=> getCommitWithFiles({}, {}, {})", group, repository, hashOrRef)
        return client.getCommit(group, repository, hashOrRef, true).toCommitWithFiles(getRepository(group, repository))
            .also { log.trace("<= getCommitWithFiles({}, {}, {}): {}", group, repository, hashOrRef, it) }
    }

    fun getPullRequestReviews(group: String, repository: String, number: Long): List<GiteaPullRequestReview> {
        log.trace("=> getPullRequestReviews({}, {}, {})", group, repository, number)
        return client.getPullRequestReviews(group, repository, number)
            .also { log.trace("<= getPullRequestReviews({}, {}, {}): {}", group, repository, number, it) }
    }

    override fun getPullRequests(group: String, repository: String): Sequence<PullRequest> {
        log.trace("=> getPullRequests({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            try {
                client.getPullRequests(group, repository)
            } catch (e: NotFoundException) {
                emptyList() //for some reason Gitea returns 404 in case of empty repository
            }.asSequence().map {
                it.toPullRequest(this, getPullRequestReviews(group, repository, it.number))
            }
        }.also { if (log.isTraceEnabled) log.trace("<= getPullRequests({}, {}): {}", group, repository, it.toList()) }
    }

    override fun createPullRequest(
        group: String, repository: String, createPullRequest: CreatePullRequest
    ): PullRequest {
        log.trace("=> getPullRequests({}, {}, {})", group, repository, createPullRequest)
        return client.createPullRequestWithDefaultReviewers(
            group,
            repository,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch,
            createPullRequest.title,
            createPullRequest.description
        ).let {
            it.toPullRequest(
                getRepository(group, repository), client.getPullRequestReviews(group, repository, it.number)
            )
        }.also { log.trace("<= getPullRequests({}, {}, {}): {}", group, repository, createPullRequest, it) }
    }

    override fun getPullRequest(group: String, repository: String, index: Long): PullRequest {
        log.trace("=> getPullRequest({}, {}, {})", group, repository, index)
        return client.getPullRequest(group, repository, index).let {
            it.toPullRequest(
                getRepository(group, repository), client.getPullRequestReviews(group, repository, it.number)
            )
        }.also { log.trace("<= getPullRequest({}, {}, {}): {}", group, repository, index, it) }
    }

    override fun findCommits(group: String, repository: String, hashes: Set<String>): Sequence<Commit> {
        log.trace("=> findCommits({}, {}, {})", group, repository, hashes)
        return with(getRepository(group, repository)) {
            hashes.mapNotNull {
                try {
                    client.getCommit(group, repository, it).toCommit(this)
                } catch (e: NotFoundException) {
                    null
                }
            }.asSequence()
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= findCommits({}, {}, {}): {}",
                group,
                repository,
                hashes,
                it.toList()
            )
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>): Sequence<PullRequest> {
        log.trace("=> findPullRequests({}, {}, {})", group, repository, indexes)
        return with(getRepository(group, repository)) {
            indexes.mapNotNull {
                try {
                    client.getPullRequest(group, repository, it)
                        .toPullRequest(this, client.getPullRequestReviews(group, repository, it))
                } catch (e: NotFoundException) {
                    null
                }
            }.asSequence()
        }.also {
            if (log.isTraceEnabled) log.trace(
                "<= findPullRequests({}, {}, {}): {}",
                group,
                repository,
                indexes,
                it.toList()
            )
        }
    }

    override fun findBranches(issueKey: String): Sequence<Branch> {
        log.warn("There is no native implementation of findBranches")
        return emptySequence()
    }

    override fun findCommits(issueKey: String): Sequence<Commit> {
        log.warn("There is no native implementation of findCommits")
        return emptySequence()
    }

    override fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles> {
        log.warn("There is no native implementation of findCommitsWithFiles")
        return emptySequence()
    }

    override fun findPullRequests(issueKey: String): Sequence<PullRequest> {
        log.warn("There is no native implementation of findPullRequests")
        return emptySequence()
    }

    fun toRepository(giteaRepository: GiteaRepository): Repository {
        val repository = giteaRepository.name.lowercase()
        val organization = giteaRepository.fullName.lowercase().removeSuffix("/$repository")
        return Repository("$sshUrl/$organization/$repository.git", //TODO: add "useColon" parameter?
            "$httpUrl/$organization/$repository",
            //IMPORTANT: see https://github.com/go-gitea/gitea/pull/31187
            //Gitea versions 1.22.0 and 1.22.1 return host url instead of empty string as avatar_url for repository with no avatar
            giteaRepository.avatarUrl.let {
                val path = try {
                    URI(it).path
                } catch (e: URISyntaxException) {
                    ""
                }
                if (path.trim('/').isEmpty()) null else it
            }
            //TODO: restore `giteaRepository.avatarUrl.ifBlank { null }` after Gitea fix
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(GiteaService::class.java)

        fun GiteaBranch.toBranch(repository: Repository) = Branch(
            name, commit.id, "${repository.link}/src/branch/$name", repository
        )

        fun GiteaTag.toTag(repository: Repository) = Tag(
            name, commit.sha, "${repository.link}/src/tag/$name", repository
        )

        private fun GiteaUser.toUser() = User(username, avatarUrl.ifBlank { null })

        private fun GiteaCommit.toCommit(repository: Repository) = Commit(
            sha,
            commit.message,
            created,
            author?.toUser() ?: User(commit.author.name),
            parents.map { it.sha },
            "${repository.link}/commit/$sha",
            repository
        )

        private fun String.sha1() = BigInteger(1, MessageDigest.getInstance("SHA-1").digest(toByteArray()))
            .toString(16).padStart(32, '0')

        private fun GiteaCommit.toCommitWithFiles(repository: Repository) = with(toCommit(repository)) {
            val fileChanges = files!!.map {
                FileChange(
                    when (it.status) {
                        GiteaCommit.GiteaCommitAffectedFileStatus.ADDED -> FileChangeType.ADD
                        GiteaCommit.GiteaCommitAffectedFileStatus.MODIFIED -> FileChangeType.MODIFY
                        GiteaCommit.GiteaCommitAffectedFileStatus.REMOVED -> FileChangeType.DELETE
                    }, it.filename, "${this.link}#diff-${it.filename.sha1()}"
                )
            }
            CommitWithFiles(this, fileChanges.size, fileChanges)
        }

        fun GiteaPullRequest.toPullRequest(
            repository: Repository, giteaPullRequestReviews: List<GiteaPullRequestReview>
        ): PullRequest {
            val approvedGiteaUserIds = giteaPullRequestReviews.filter {
                it.state == GiteaPullRequestReview.GiteaPullRequestReviewState.APPROVED && !it.dismissed
            }.mapNotNull { it.user?.id }.toSet()
            return PullRequest(
                number,
                title,
                body,
                user.toUser(),
                head.label,
                base.label,
                assignees.map { it.toUser() },
                requestedReviewers.map { PullRequestReviewer(it.toUser(), approvedGiteaUserIds.contains(it.id)) },
                if (merged) {
                    PullRequestStatus.MERGED
                } else if (state == GiteaPullRequest.GiteaPullRequestState.CLOSED) {
                    PullRequestStatus.DECLINED
                } else {
                    PullRequestStatus.OPEN
                },
                createdAt,
                updatedAt,
                "${repository.link}/pulls/$number",
                repository
            )
        }
    }
}
