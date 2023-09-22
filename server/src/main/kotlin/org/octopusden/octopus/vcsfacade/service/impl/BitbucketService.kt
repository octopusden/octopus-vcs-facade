package org.octopusden.octopus.vcsfacade.service.impl

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketLinkName
import org.octopusden.octopus.infrastructure.bitbucket.client.getBranches
import org.octopusden.octopus.infrastructure.bitbucket.client.getCommits
import org.octopusden.octopus.infrastructure.bitbucket.client.getTags
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.service.VCSClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Date

@Service
@ConditionalOnProperty(prefix = "vcs-facade.vcs.bitbucket", name = ["enabled"], havingValue = "true", matchIfMissing = true)
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
    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): Collection<Commit> {
        validateParams(fromId, fromDate)
        val (project, repository) = vcsPath.toProjectAndRepository()
        val bitbucketCommits = execute("getCommits($vcsPath, $fromId, $fromDate, $toId)") {
            bitbucketClient.getCommits(project, repository, fromId, fromDate, toId)
        }

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
        return execute("getCommits($issueKey)") { bitbucketClient.getCommits(issueKey) }
            .map { c ->
                with(c.toCommit) {
                    val vcsUrl =
                        (c.repository.links.clone.find { it.name == BitbucketLinkName.SSH }
                            ?.href
                            ?: throw IllegalStateException("Repository SSH Link must be present"))
                    Commit(id, message, authorTimestamp, author.name, parents.map { p -> p.id }, vcsUrl)
                }
            }
    }

    override fun getTags(vcsPath: String): List<Tag> {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return execute("getTags($vcsPath)") { bitbucketClient.getTags(project, repository) }
            .map { Tag(it.latestCommit, it.displayId) }
    }

    override fun getCommit(vcsPath: String, commitIdOrRef: String): Commit {
        val (project, repository) = vcsPath.toProjectAndRepository()
        return with(
            execute("getCommit($vcsPath, $commitIdOrRef)") {
                bitbucketClient.getCommit(
                    project,
                    repository,
                    getBranchLatestCommit(project, repository, commitIdOrRef) ?: commitIdOrRef
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

    override fun getLog(): Logger = log

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
        private fun <T> execute(errorMessage: String, clientFunction: () -> T): T {
            try {
                return clientFunction.invoke()
            } catch (e: org.octopusden.octopus.infrastructure.bitbucket.client.exception.NotFoundException) {
                log.error("$errorMessage: ${e.message}")
                throw NotFoundException(e.message ?: e::class.qualifiedName!!)
            }
        }
    }
}
