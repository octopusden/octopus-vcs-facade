package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketBranch
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketPullRequest
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketPullRequestState
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketTag
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketUser
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException
import org.octopusden.octopus.infrastructure.bitbucket.client.getBranches
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommits
import org.octopusden.octopus.infrastructure.bitbucket.client.getTags
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.config.VcsConfig
import org.octopusden.octopus.vcsfacade.service.VcsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.bitbucket", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class BitbucketService(
    bitbucketProperties: VcsConfig.BitbucketProperties,
) : VcsService(bitbucketProperties) {
    private val bitbucketClient: BitbucketClient = BitbucketClassicClient(object : BitbucketClientParametersProvider {
        override fun getApiUrl(): String = httpUrl

        override fun getAuth(): BitbucketCredentialProvider {
            val authException by lazy {
                IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
            }
            return bitbucketProperties.token?.let { BitbucketBearerTokenCredentialProvider(it) }
                ?: BitbucketBasicCredentialProvider(
                    bitbucketProperties.username ?: throw authException,
                    bitbucketProperties.password ?: throw authException
                )
        }
    })

    override val sshUrlRegex = "(?:ssh://)?git@$host/([^/]+)/([^/]+).git".toRegex()

    override fun getSshUrl(group: String, repository: String) = "ssh://git@$host/$group/$repository.git"

    override fun getBranches(group: String, repository: String): List<Branch> {
        log.trace("=> getBranches({}, {})", group, repository)
        return bitbucketClient.getBranches(group, repository).map { it.toBranch(group, repository) }.also {
            log.trace("<= getBranches({}, {}): {}", group, repository, it)
        }
    }

    override fun getTags(group: String, repository: String): List<Tag> {
        log.trace("=> getTags({}, {})", group, repository)
        return bitbucketClient.getTags(group, repository).map { it.toTag(group, repository) }.also {
            log.trace("<= getTags({}, {}): {}", group, repository, it)
        }
    }

    override fun getCommits(group: String, repository: String, toId: String, fromId: String): List<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, toId, fromId)
        return bitbucketClient.getCommits(group, repository, toId, fromId).map { it.toCommit(group, repository) }.also {
            log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, toId, fromId, it)
        }
    }

    override fun getCommits(group: String, repository: String, toId: String, fromDate: Date?): List<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, toId, fromDate)
        return bitbucketClient.getCommits(group, repository, toId, fromDate).map { it.toCommit(group, repository) }
            .also { log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, toId, fromDate, it) }
    }

    override fun getCommit(group: String, repository: String, id: String): Commit {
        log.trace("=> getCommit({}, {}, {})", group, repository, id)
        return bitbucketClient.getCommit(group, repository, id).toCommit(group, repository).also {
            log.trace("<= getCommit({}, {}, {}): {}", group, repository, id, it)
        }
    }

    override fun createPullRequest(
        group: String, repository: String, createPullRequest: CreatePullRequest
    ): PullRequest {
        log.trace("=> createPullRequest({}, {}, {})", group, repository, createPullRequest)
        return bitbucketClient.createPullRequestWithDefaultReviewers(
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
        return bitbucketClient.getPullRequest(group, repository, index).toPullRequest(group, repository).also {
            log.trace("<= getPullRequest({}, {}, {}): {}", group, repository, index, it)
        }
    }

    override fun findCommits(group: String, repository: String, ids: Set<String>): List<Commit> {
        log.trace("=> findCommits({}, {}, {})", group, repository, ids)
        return ids.mapNotNull {
            try {
                bitbucketClient.getCommit(group, repository, it).toCommit(group, repository)
            } catch (e: NotFoundException) {
                null
            }
        }.also {
            log.trace("<= findCommits({}, {}, {}): {}", group, repository, ids, it)
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>): List<PullRequest> {
        log.trace("=> findPullRequests({}, {}, {})", group, repository, indexes)
        return indexes.mapNotNull {
            try {
                bitbucketClient.getPullRequest(group, repository, it).toPullRequest(group, repository)
            } catch (e: NotFoundException) {
                null
            }
        }.also {
            log.trace("<= findPullRequests({}, {}, {}): {}", group, repository, indexes, it)
        }
    }

    override fun findBranches(issueKey: String): List<Branch> {
        log.warn("There is no native implementation of findBranches")
        return emptyList()
    }

    override fun findCommits(issueKey: String): List<Commit> {
        log.trace("=> findCommits({})", issueKey)
        return bitbucketClient.getCommits(issueKey).map {
            it.toCommit.toCommit(it.repository.project.key.lowercase(), it.repository.slug.lowercase())
        }.also {
            log.trace("<= findCommits({}): {}", issueKey, it)
        }
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.warn("There is no native implementation of findPullRequests")
        return emptyList()
    }

    private fun getRepository(project: String, repository: String) = Repository(
        getSshUrl(project, repository),
        "$httpUrl/projects/$project/repos/$repository/browse",
        "$httpUrl/projects/$project/avatar.png?s=48"
    )

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
                listOf(author),
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
