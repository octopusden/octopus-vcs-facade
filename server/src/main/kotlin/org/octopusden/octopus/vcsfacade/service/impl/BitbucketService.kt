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
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketLinkName
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketPullRequest
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketPullRequestState
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketTag
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketUser
import org.octopusden.octopus.infrastructure.bitbucket.client.getBranches
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommits
import org.octopusden.octopus.infrastructure.bitbucket.client.getTags
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestStatus
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException as BitBucketNotFoundException

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.bitbucket",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class BitbucketService(
    bitbucketProperties: VCSConfig.BitbucketProperties,
) : VCSClient(bitbucketProperties, VcsServiceType.BITBUCKET) {
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

    override fun getBranches(group: String, repository: String) =
        execute("getBranches($group, $repository)") {
            bitbucketClient.getBranches(group, repository)
        }.map { it.toBranch(group, repository) }

    override fun getTags(group: String, repository: String) =
        execute("getTags($group, $repository)") {
            bitbucketClient.getTags(group, repository)
        }.map { it.toTag(group, repository) }

    override fun getCommits(group: String, repository: String, toId: String, fromId: String) =
        execute("getCommits($group, $repository, $toId, $fromId)") {
            bitbucketClient.getCommits(group, repository, toId, fromId)
        }.map { it.toCommit(group, repository) }

    override fun getCommits(group: String, repository: String, toId: String, fromDate: Date?) =
        execute("getCommits($group, $repository, $toId, $fromDate)") {
            bitbucketClient.getCommits(group, repository, toId, fromDate)
        }.map { it.toCommit(group, repository) }

    override fun getCommit(group: String, repository: String, id: String) =
        execute("getCommit($group, $repository, $id)") {
            bitbucketClient.getCommit(group, repository, id)
        }.toCommit(group, repository)

    override fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest) =
        execute("createPullRequest($group, $repository, $createPullRequest)") {
            bitbucketClient.createPullRequestWithDefaultReviewers(
                group,
                repository,
                createPullRequest.sourceBranch,
                createPullRequest.targetBranch,
                createPullRequest.title,
                createPullRequest.description
            ).toPullRequest(group, repository)
        }

    override fun getPullRequest(group: String, repository: String, index: Long) =
        execute("getPullRequest($group, $repository, $index)") {
            bitbucketClient.getPullRequest(group, repository, index).toPullRequest(group, repository)
        }

    override fun findCommits(group: String, repository: String, ids: Set<String>) = ids.mapNotNull {
        try {
            bitbucketClient.getCommit(group, repository, it).toCommit(group, repository)
        } catch (e: BitBucketNotFoundException) {
            null
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>) = indexes.mapNotNull {
        try {
            bitbucketClient.getPullRequest(group, repository, it).toPullRequest(group, repository)
        } catch (e: BitBucketNotFoundException) {
            null
        }
    }

    override fun findBranches(issueKey: String): List<Branch> {
        log.warn("There is no native implementation of findBranches for $vcsServiceType")
        return emptyList()
    }

    override fun findCommits(issueKey: String) =
        execute("findCommits($issueKey)") {
            bitbucketClient.getCommits(issueKey)
        }.map { bitbucketJiraCommit ->
            val (group, repository) = parse(bitbucketJiraCommit.repository.links.clone.find { it.name == BitbucketLinkName.SSH }!!.href)
            bitbucketJiraCommit.toCommit.toCommit(group, repository)
        }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.warn("There is no native implementation of findPullRequests for $vcsServiceType")
        return emptyList()
    }

    private fun getRepository(project: String, repository: String) = Repository(
        "ssh://git@$host/$project/$repository.git",
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
                reviewers.map { it.user.toUser() },
                when (state) {
                    BitbucketPullRequestState.MERGED -> PullRequestStatus.MERGED
                    BitbucketPullRequestState.DECLINED -> PullRequestStatus.DECLINED
                    BitbucketPullRequestState.OPEN -> PullRequestStatus.OPENED
                },
                createdDate,
                updatedDate,
                "$httpUrl/projects/$project/repos/$repository/pull-requests/$id/overview",
                getRepository(project, repository)
            )
        }

    companion object {
        private val log = LoggerFactory.getLogger(BitbucketService::class.java)

        private fun <T> execute(errorMessage: String, clientFunction: () -> T): T {
            try {
                return clientFunction.invoke()
            } catch (e: BitBucketNotFoundException) {
                log.error("$errorMessage: ${e.message}")
                throw NotFoundException(e.message ?: e::class.qualifiedName!!)
            }
        }
    }
}
