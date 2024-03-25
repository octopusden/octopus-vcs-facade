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
        override fun getApiUrl(): String = url

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

    override val vcsPathRegex = "(?:ssh://)?git@$host/([^/]+)/([^/]+).git".toRegex()

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

    override fun getCommit(group: String, repository: String, commitIdOrRef: String) =
        execute("getCommit($group, $repository, $commitIdOrRef)") {
            bitbucketClient.getCommit(group, repository, commitIdOrRef)
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

    override fun findBranches(issueKey: String): List<Branch> {
        log.warn("There is no native implementation of findBranches for $vcsServiceType")
        return emptyList()
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.warn("There is no native implementation of findPullRequests for $vcsServiceType")
        return emptyList()
    }

    override fun findCommits(issueKey: String) =
        execute("findCommits($issueKey)") {
            bitbucketClient.getCommits(issueKey)
        }.map { bitbucketJiraCommit ->
            val (group, repository) = parseRepository(bitbucketJiraCommit.repository.links.clone.find { it.name == BitbucketLinkName.SSH }!!.href)
            bitbucketJiraCommit.toCommit.toCommit(group, repository)
        }

    private fun getVcsUrl(project: String, repository: String) = "ssh://git@$host/$project/$repository.git"

    private fun BitbucketBranch.toBranch(project: String, repository: String) = Branch(
        displayId,
        latestCommit,
        "$url/projects/$project/repos/$repository/browse?at=$displayId",
        getVcsUrl(project, repository)
    )

    private fun BitbucketTag.toTag(project: String, repository: String) = Tag(
        displayId,
        latestCommit,
        "$url/projects/$project/repos/$repository/browse?at=$displayId",
        getVcsUrl(project, repository)
    )

    private fun getAvatarUrl(username: String) = "$url/users/$username/avatar.png?s=48"

    private fun BitbucketUser.toUser() = User(name, getAvatarUrl(name))

    private fun BitbucketCommit.toCommit(project: String, repository: String) = Commit(
        id,
        message,
        authorTimestamp,
        author.toUser(),
        parents.map { it.id },
        "$url/projects/$project/repos/$repository/commits/$id",
        getVcsUrl(project, repository)
    )

    private fun BitbucketPullRequest.toPullRequest(project: String, repository: String) = PullRequest(
        id,
        title,
        description ?: "",
        fromRef.displayId,
        toRef.displayId,
        listOf(User(author.user.name, getAvatarUrl(author.user.name))),
        reviewers.map { User(it.user.name, getAvatarUrl(it.user.name)) },
        when(state) {
            BitbucketPullRequestState.MERGED -> PullRequestStatus.MERGED
            BitbucketPullRequestState.DECLINED -> PullRequestStatus.DECLINED
            BitbucketPullRequestState.OPEN -> PullRequestStatus.OPENED
        },
        createdDate,
        updatedDate,
        "$url/projects/$project/repos/$repository/pull-requests/$id/overview",
        getVcsUrl(project, repository)
    )

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
