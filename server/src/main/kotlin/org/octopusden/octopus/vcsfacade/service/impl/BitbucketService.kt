package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketBranch
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCommitChange
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCreateTag
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketPullRequest
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketPullRequestState
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketRepository
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketTag
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketUser
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException
import org.octopusden.octopus.infrastructure.bitbucket.client.getBranches
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommitChanges
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommits
import org.octopusden.octopus.infrastructure.bitbucket.client.getRepositories
import org.octopusden.octopus.infrastructure.bitbucket.client.getTag
import org.octopusden.octopus.infrastructure.bitbucket.client.getTags
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
import org.octopusden.octopus.vcsfacade.service.VcsService
import org.slf4j.LoggerFactory

class BitbucketService(
    vcsServiceProperties: VcsProperties.Service
) : VcsService(vcsServiceProperties) {
    private val client: BitbucketClient = BitbucketClassicClient(object : BitbucketClientParametersProvider {
        override fun getApiUrl(): String = httpUrl
        override fun getAuth() = vcsServiceProperties.getCredentialProvider() as BitbucketCredentialProvider
    })

    override fun getRepositories(): Sequence<Repository> {
        log.trace("=> getRepositories()")
        return client.getRepositories().asSequence().map { it.toRepository() }
            .also { log.trace("<= getRepositories(): {}", it) }
    }

    override fun findRepository(group: String, repository: String): Repository? {
        log.trace("=> findRepository({}, {})", group, repository)
        return try {
            client.getRepository(group, repository).toRepository()
        } catch (e: NotFoundException) {
            null
        }.also { log.trace("<= findRepository({}, {}): {}", group, repository, it) }
    }

    override fun getBranches(group: String, repository: String): Sequence<Branch> {
        log.trace("=> getBranches({}, {})", group, repository)
        return client.getBranches(group, repository).asSequence().map {
            it.toBranch(group, repository)
        }.also { log.trace("<= getBranches({}, {}): {}", group, repository, it) }
    }

    override fun getTags(group: String, repository: String): Sequence<Tag> {
        log.trace("=> getTags({}, {})", group, repository)
        return client.getTags(group, repository).asSequence().map {
            it.toTag(group, repository)
        }.also { log.trace("<= getTags({}, {}): {}", group, repository, it) }
    }

    override fun createTag(group: String, repository: String, createTag: CreateTag): Tag {
        log.trace("=> createTag({}, {}, {})", group, repository, createTag)
        return client.createTag(
            group, repository, BitbucketCreateTag(createTag.name, createTag.hashOrRef, createTag.message)
        ).toTag(group, repository).also {
            log.trace("<= createTag({}, {}, {}): {}", group, repository, createTag, it)
        }
    }

    override fun getTag(group: String, repository: String, name: String): Tag {
        log.trace("=> getTag({}, {}, {})", group, repository, name)
        return client.getTag(group, repository, name).toTag(group, repository).also {
            log.trace("<= getTag({}, {}, {}): {}", group, repository, name, it)
        }
    }

    override fun deleteTag(group: String, repository: String, name: String) {
        log.trace("=> deleteTag({}, {}, {})", group, repository, name)
        client.deleteTag(group, repository, name)
        log.trace("<= deleteTag({}, {}, {})", group, repository, name)
    }

    override fun getCommits(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String
    ): Sequence<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, from, toHashOrRef)
        val commits = if (from is HashOrRefOrDate.HashOrRefValue) {
            client.getCommits(group, repository, toHashOrRef, from.value)
        } else {
            client.getCommits(group, repository, toHashOrRef, (from as? HashOrRefOrDate.DateValue)?.value)
        }
        return commits.asSequence().map { it.toCommit(group, repository) }.also {
            log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, from, toHashOrRef, it)
        }
    }

    override fun getCommitsWithFiles(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String
    ): Sequence<CommitWithFiles> {
        log.trace("=> getCommitsWithFiles({}, {}, {}, {})", group, repository, from, toHashOrRef)
        return getCommits(group, repository, from, toHashOrRef).map {
            val fileChanges = getCommitChanges(group, repository, it)
            CommitWithFiles(it, fileChanges.size, fileChanges)
        }.also { log.trace("<= getCommitsWithFiles({}, {}, {}, {}): {}", group, repository, from, toHashOrRef, it) }
    }

    override fun getCommit(group: String, repository: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {}, {})", group, repository, hashOrRef)
        return client.getCommit(group, repository, hashOrRef).toCommit(group, repository).also {
            log.trace("<= getCommit({}, {}, {}): {}", group, repository, hashOrRef, it)
        }
    }

    override fun getCommitWithFiles(group: String, repository: String, hashOrRef: String): CommitWithFiles {
        log.trace("=> getCommitWithFiles({}, {}, {})", group, repository, hashOrRef)
        return with(getCommit(group, repository, hashOrRef)) {
            val fileChanges = getCommitChanges(group, repository, this)
            CommitWithFiles(this, fileChanges.size, fileChanges)
        }.also { log.trace("<= getCommitWithFiles({}, {}, {}): {}", group, repository, hashOrRef, it) }
    }

    override fun getBranchesCommitGraph(group: String, repository: String): Sequence<CommitWithFiles> {
        TODO("Indexing of BitBucket repositories is not supported yet")
    }

    override fun getPullRequests(group: String, repository: String): Sequence<PullRequest> {
        TODO("Indexing of BitBucket repositories is not supported yet")
    }

    override fun createPullRequest(
        group: String, repository: String, createPullRequest: CreatePullRequest
    ): PullRequest {
        log.trace("=> createPullRequest({}, {}, {})", group, repository, createPullRequest)
        return client.createPullRequestWithDefaultReviewers(
            group,
            repository,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch,
            createPullRequest.title,
            createPullRequest.description
        ).toPullRequest(group, repository).also {
            log.trace("<= createPullRequest({}, {}, {}): {}", group, repository, createPullRequest, it)
        }
    }

    override fun getPullRequest(group: String, repository: String, index: Long): PullRequest {
        log.trace("=> getPullRequest({}, {}, {})", group, repository, index)
        return client.getPullRequest(group, repository, index).toPullRequest(group, repository).also {
            log.trace("<= getPullRequest({}, {}, {}): {}", group, repository, index, it)
        }
    }

    override fun findCommits(group: String, repository: String, hashes: Set<String>): Sequence<Commit> {
        log.trace("=> findCommits({}, {}, {})", group, repository, hashes)
        return hashes.mapNotNull {
            try {
                client.getCommit(group, repository, it).toCommit(group, repository)
            } catch (e: NotFoundException) {
                null
            }
        }.asSequence().also {
            log.trace("<= findCommits({}, {}, {}): {}", group, repository, hashes, it)
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>): Sequence<PullRequest> {
        log.trace("=> findPullRequests({}, {}, {})", group, repository, indexes)
        return indexes.mapNotNull {
            try {
                client.getPullRequest(group, repository, it).toPullRequest(group, repository)
            } catch (e: NotFoundException) {
                null
            }
        }.asSequence().also {
            log.trace("<= findPullRequests({}, {}, {}): {}", group, repository, indexes, it)
        }
    }

    override fun findBranches(issueKey: String): Sequence<Branch> {
        log.warn("There is no native implementation of findBranches")
        return emptySequence()
    }

    override fun findCommits(issueKey: String): Sequence<Commit> {
        log.trace("=> findCommits({})", issueKey)
        return client.getCommits(issueKey).asSequence().map {
            it.toCommit.toCommit(it.repository.project.key.lowercase(), it.repository.slug.lowercase())
        }.also { log.trace("<= findCommits({}): {}", issueKey, it) }
    }

    override fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles> {
        log.trace("=> findCommitsWithFiles({})", issueKey)
        return client.getCommits(issueKey).asSequence().map {
            val group = it.repository.project.key.lowercase()
            val repository = it.repository.slug.lowercase()
            val commit = it.toCommit.toCommit(group, repository)
            val fileChanges = getCommitChanges(group, repository, commit)
            CommitWithFiles(commit, fileChanges.size, fileChanges)
        }.also { log.trace("<= findCommitsWithFiles({}): {}", issueKey, it) }
    }

    override fun findPullRequests(issueKey: String): Sequence<PullRequest> {
        log.warn("There is no native implementation of findPullRequests")
        return emptySequence()
    }

    private fun getCommitChanges(group: String, repository: String, commit: Commit): List<FileChange> {
        log.trace("=> getCommitChanges({}, {}, {})", group, repository, commit)
        return client.getCommitChanges(group, repository, commit.hash).flatMap {
            val files = mutableListOf(
                FileChange(
                    when (it.type) {
                        BitbucketCommitChange.BitbucketCommitChangeType.ADD,
                        BitbucketCommitChange.BitbucketCommitChangeType.COPY,
                        BitbucketCommitChange.BitbucketCommitChangeType.MOVE -> FileChangeType.ADD

                        BitbucketCommitChange.BitbucketCommitChangeType.MODIFY -> FileChangeType.MODIFY

                        BitbucketCommitChange.BitbucketCommitChangeType.DELETE -> FileChangeType.DELETE
                    },
                    it.path.value,
                    "${commit.link}#${it.path.value}"
                )
            )
            if (it.type == BitbucketCommitChange.BitbucketCommitChangeType.MOVE) {
                files.add(FileChange(FileChangeType.DELETE, it.srcPath!!.value, "${commit.link}#${it.srcPath!!.value}"))
            }
            files
        }.also { log.trace("<= getCommitChanges({}, {}, {}): {}", group, repository, commit, it) }
    }

    private fun getRepository(project: String, repository: String) = Repository(
        "$sshUrl/$project/$repository.git",
        "$httpUrl/projects/$project/repos/$repository/browse",
        "$httpUrl/projects/$project/avatar.png?s=48"
    )

    private fun BitbucketRepository.toRepository() = getRepository(project.key.lowercase(), slug.lowercase())

    private fun BitbucketBranch.toBranch(project: String, repository: String) = Branch(
        displayId,
        latestCommit,
        "$httpUrl/projects/$project/repos/$repository/browse?at=$displayId",
        getRepository(project, repository)
    )

    private fun BitbucketTag.toTag(project: String, repository: String) = Tag(
        displayId,
        latestCommit,
        "$httpUrl/projects/$project/repos/$repository/browse?at=$displayId",
        getRepository(project, repository)
    )

    private fun BitbucketUser.toUser() = User(name, "$httpUrl/users/$name/avatar.png?s=48")

    private fun BitbucketCommit.toCommit(project: String, repository: String) = Commit(
        id,
        message,
        authorTimestamp,
        author.toUser(),
        parents.map { it.id },
        "$httpUrl/projects/$project/repos/$repository/commits/$id",
        getRepository(project, repository)
    )

    private fun BitbucketPullRequest.toPullRequest(project: String, repository: String) =
        author.user.toUser().let { author ->
            PullRequest(
                id,
                title,
                description ?: "",
                author,
                fromRef.displayId,
                toRef.displayId,
                emptyList(),
                reviewers.map { PullRequestReviewer(it.user.toUser(), it.approved) },
                when (state) {
                    BitbucketPullRequestState.MERGED -> PullRequestStatus.MERGED
                    BitbucketPullRequestState.DECLINED -> PullRequestStatus.DECLINED
                    BitbucketPullRequestState.OPEN -> PullRequestStatus.OPEN
                },
                createdDate,
                updatedDate,
                "$httpUrl/projects/$project/repos/$repository/pull-requests/$id/overview",
                getRepository(project, repository)
            )
        }

    companion object {
        private val log = LoggerFactory.getLogger(BitbucketService::class.java)
    }
}
