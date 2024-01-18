package org.octopusden.octopus.vcsfacade.service.impl

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketLinkName
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommits
import org.octopusden.octopus.infrastructure.bitbucket.client.getTags
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Date
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
) : VCSClient(bitbucketProperties) {
    private val bitbucketClient: BitbucketClient = BitbucketClassicClient(object : BitbucketClientParametersProvider {
        override fun getApiUrl(): String = bitbucketProperties.host

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

    private fun String.toProjectAndRepository() =
        vcsPathRegex.find(this.lowercase())!!.destructured.let { it.component1() to it.component2() }

    override fun getCommits(vcsPath: String, toId: String, fromId: String): Collection<Commit> {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("getCommits($vcsPath, $toId, $fromId)") {
            bitbucketClient.getCommits(project, repository, toId, fromId)
        }.map { it.toCommit(vcsPath) }
    }

    override fun getCommits(vcsPath: String, toId: String, fromDate: Date?): Collection<Commit> {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("getCommits($vcsPath, $toId, $fromDate)") {
            bitbucketClient.getCommits(project, repository, toId, fromDate)
        }.map { it.toCommit(vcsPath) }
    }

    override fun getCommits(issueKey: String): List<Commit> {
        return execute("getCommits($issueKey)") {
            bitbucketClient.getCommits(issueKey)
        }.map { c ->
            val vcsUrl = (c.repository.links.clone.find { it.name == BitbucketLinkName.SSH }?.href
                ?: throw IllegalStateException("Repository SSH Link must be present"))
            c.toCommit.toCommit(vcsUrl)
        }
    }

    override fun getTags(vcsPath: String): List<Tag> {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("getTags($vcsPath)") {
            bitbucketClient.getTags(project, repository)
        }.map { Tag(it.latestCommit, it.displayId) }
    }

    override fun getCommit(vcsPath: String, commitIdOrRef: String): Commit {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("getCommit($vcsPath, $commitIdOrRef)") {
            bitbucketClient.getCommit(project, repository, commitIdOrRef)
        }.toCommit(vcsPath)
    }

    override fun createPullRequest(
        vcsPath: String, pullRequestRequest: PullRequestRequest
    ): PullRequestResponse {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("createPullRequest($vcsPath, $pullRequestRequest, ${pullRequestRequest.targetBranch})") {
            val pullRequest = bitbucketClient.createPullRequestWithDefaultReviewers(
                project,
                repository,
                pullRequestRequest.sourceBranch,
                pullRequestRequest.targetBranch,
                pullRequestRequest.title,
                pullRequestRequest.description
            )
            PullRequestResponse(pullRequest.id)
        }
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

        private fun BitbucketCommit.toCommit(vcsPath: String) =
            Commit(id, message, authorTimestamp, author.name, parents.map { it.id }, vcsPath)
    }
}
