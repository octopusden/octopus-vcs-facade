package org.octopusden.vcsfacade.service.impl

import org.octopusden.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.infrastructure.bitbucket.client.createPullRequestWithDefaultReviewers
import org.octopusden.infrastructure.bitbucket.client.getBranches
import org.octopusden.infrastructure.bitbucket.client.getCommits
import org.octopusden.infrastructure.bitbucket.client.getTags
import org.octopusden.vcsfacade.client.common.dto.Commit
import org.octopusden.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.vcsfacade.client.common.dto.Tag
import org.octopusden.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.vcsfacade.config.VCSConfig
import org.octopusden.vcsfacade.service.VCSClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Date
import org.octopusden.infrastructure.bitbucket.client.exception.NotFoundException as BitbucketClientNotFoundException

@Service
@ConditionalOnProperty(prefix = "bitbucket", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class BitbucketService(
    bitbucketProperties: VCSConfig.BitbucketProperties,
) : VCSClient(bitbucketProperties) {

    override val repoPrefix: String = "ssh://git@"

    private val bitbucketClient: BitbucketClient =
        BitbucketClassicClient(
            object : BitbucketClientParametersProvider {
                override fun getApiUrl(): String = bitbucketProperties.host

                override fun getAuth(): BitbucketCredentialProvider {
                    val authException by lazy {
                        IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
                    }
                    return bitbucketProperties.token
                        ?.let { BitbucketBearerTokenCredentialProvider(it) }
                        ?: BitbucketBasicCredentialProvider(
                            bitbucketProperties.username ?: throw authException,
                            bitbucketProperties.password ?: throw authException
                        )
                }
            }
        )

    /**
     * fromId and fromDate are not works together, must be specified one of it or not one
     */
    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit> {
        validateParams(fromId, fromDate)
        val requestParameters = baseRequestParameters()
        requestParameters["until"] = toId

        fromId?.let { fromIdValue ->
            requestParameters["since"] = fromIdValue
        }
        val (project, repository) = vcsPath.toProjectAndRepository()
        val bitbucketCommits = execute("") { bitbucketClient.getCommits(project, repository, fromId, fromDate, toId) }

        fromId?.let { fromIdValue ->
            if (fromIdValue != execute("") { bitbucketClient.getCommit(project, repository, toId) }.id
                && !bitbucketCommits.any { bc -> bc.parents.any { p -> p.id == fromIdValue } }
            ) {
                throw NotFoundException("Can't find commit '$fromIdValue' in graph but it exists in the '$vcsPath'")
            }
        }

        return bitbucketCommits.map { c ->
            Commit(c.id, c.message, c.authorTimestamp, c.author.name, c.parents.map { p -> p.id }, vcsPath)
        }
    }

    override fun getCommits(issueKey: String): List<Commit> {
        return execute("") { bitbucketClient.getCommits(issueKey) }
            .map { c ->
                with(c.toCommit) {
                    val vcsUrl =
                        (c.repository.links.clone.find { it.name == org.octopusden.infrastructure.bitbucket.client.dto.BitbucketLinkName.SSH }
                            ?.href
                            ?: throw IllegalStateException("Repository SSH Link must be present"))
                    Commit(id, message, authorTimestamp, author.name, parents.map { p -> p.id }, vcsUrl)
                }
            }
    }

    override fun getTags(vcsPath: String): List<Tag> {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("") { bitbucketClient.getTags(project, repository) }
            .map { Tag(it.latestCommit, it.displayId) }
    }

    override fun getCommit(vcsPath: String, commitId: String): Commit {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return with(
            execute("getCommit($vcsPath, $commitId)") {
                bitbucketClient.getCommit(
                    project,
                    repository,
                    getBranchLatestCommit(project, repository, commitId) ?: commitId
                )
            }
        ) {
            Commit(id, message, authorTimestamp, author.name, parents.map { p -> p.id }, vcsPath)
        }
    }

    override fun createPullRequest(
        vcsPath: String,
        pullRequestRequest: PullRequestRequest
    ): PullRequestResponse {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("createPullRequest($vcsPath, $pullRequestRequest, ${pullRequestRequest.targetBranch})") {
            val pullRequest =
                bitbucketClient.createPullRequestWithDefaultReviewers(
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

    private fun getBranchLatestCommit(project: String, repository: String, branchName: String): String? {
        val shortBranchName = branchName.replace("^refs/heads/".toRegex(), "")
        val fullBranchName = "refs/heads/$shortBranchName"
        return bitbucketClient.getBranches(project, repository)
            .firstOrNull { b -> b.id == fullBranchName }?.latestCommit
    }

    private fun String.toProjectAndRepository(): Pair<String, String> {
        return replace("$repoPrefix${getHost()}[^/]*/".toRegex(), "")
            .replace("\\.git$".toRegex(), "")
            .let {
                it.substringBeforeLast('/').replace("^/".toRegex(), "") to it.substringAfterLast('/')
            }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(BitbucketService::class.java)
        private fun baseRequestParameters(): MutableMap<String, Any> = mutableMapOf("limit" to 10000)
        private fun <T> execute(errorMessage: String, clientFunction: () -> T): T {
            try {
                return clientFunction.invoke()
            } catch (e: BitbucketClientNotFoundException) {
                log.error("$errorMessage: ${e.message}")
                throw NotFoundException(e.message ?: e::class.qualifiedName!!)
            }
        }
    }
}
