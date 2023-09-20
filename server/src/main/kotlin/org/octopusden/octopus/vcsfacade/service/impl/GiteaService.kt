package org.octopusden.octopus.vcsfacade.service.impl

import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.CredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClassicClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaPullRequest
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.getBranches
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import org.octopusden.octopus.infrastructure.gitea.client.getTags
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

@Service
@ConditionalOnProperty(prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class GiteaService(giteaProperties: VCSConfig.GiteaProperties) : VCSClient(giteaProperties) {

    private val client: GiteaClient = GiteaClassicClient(object : ClientParametersProvider {
        override fun getApiUrl(): String {
            return giteaProperties.host
        }

        override fun getAuth(): CredentialProvider {
            val authException by lazy {
                IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
            }
            return giteaProperties.token
                ?.let { StandardBearerTokenCredentialProvider(it) }
                ?: StandardBasicCredCredentialProvider(
                    giteaProperties.username ?: throw authException,
                    giteaProperties.password ?: throw authException
                )
        }
    })

    override val repoPrefix: String = "git@"

    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit> {
        validateParams(fromId, fromDate)
        val (organization, repository) = vcsPath.toOrganizationAndRepository()

        val toIdCommit = getCommit(vcsPath, toId)

        val calculatedFromDate =
            fromId?.let { fromIdValue ->
                execute("") { client.getCommit(organization, repository, fromIdValue).commit.author.date }
            } ?: fromDate

        val giteaCommits = execute("getCommits($vcsPath, $fromId, $fromDate, ${toIdCommit.id})") {
            client.getCommits(organization, repository, calculatedFromDate, toId)
        }

        fromId?.let { fromIdValue ->
            val fromCommit = execute("") { client.getCommit(organization, repository, toId) }

            if (fromIdValue != fromCommit.sha && giteaCommits.none { bc -> bc.parents.any { p -> p.sha == fromIdValue } }) {
                throw NotFoundException("Can't find commit '$fromIdValue' in graph but it exists in the '$vcsPath'")
            }
        }

        return giteaCommits.map { c -> c.toCommit(vcsPath) }
    }

    override fun getCommits(issueKey: String): List<Commit> {
        return emptyList()
    }

    override fun getTags(vcsPath: String): List<Tag> {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()
        return execute("getTags($vcsPath)") {
            client.getTags(organization, repository).map { giteaTag -> giteaTag.toTag() }
        }
    }

    override fun getCommit(vcsPath: String, commitId: String): Commit {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()

        return execute("getCommit($vcsPath, $commitId)") {
            client.getCommit(
                organization,
                repository,
                getBranchLatestCommit(organization, repository, commitId) ?: commitId
            ).toCommit(vcsPath)
        }
    }

    override fun createPullRequest(vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()
        return execute("createPullRequest($vcsPath, $pullRequestRequest, ${pullRequestRequest.targetBranch})") {
            client.createPullRequestWithDefaultReviewers(
                organization,
                repository,
                pullRequestRequest.sourceBranch,
                pullRequestRequest.targetBranch,
                pullRequestRequest.title,
                pullRequestRequest.description
            ).toPullRequestResponse()
        }
    }

    private fun String.toOrganizationAndRepository(): Pair<String, String> =
        replace("$repoPrefix${getHost()}[^:]*:".toRegex(), "").replace(".git$".toRegex(), "").split("/").let {
            it[0] to it[1]
        }

    private fun getBranchLatestCommit(organization: String, repository: String, branchName: String): String? {
        val shortBranchName = branchName.replace("^refs/heads/".toRegex(), "")
        return client.getBranches(organization, repository)
            .firstOrNull { b -> b.name == shortBranchName }?.commit?.id
    }

    private fun GiteaCommit.toCommit(vcsPath: String) =
        Commit(sha, commit.message, commit.author.date, commit.author.name, parents.map { it.sha }, vcsPath)

    private fun GiteaTag.toTag() = Tag(commit.sha, name)

    private fun GiteaPullRequest.toPullRequestResponse() = PullRequestResponse(id)

    companion object {

        private val log = LoggerFactory.getLogger(GiteaService::class.java)
        private fun <T> execute(errorMessage: String, clientFunction: () -> T): T {
            try {
                return clientFunction.invoke()
            } catch (e: org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException) {
                log.error("$errorMessage: ${e.message}")
                throw NotFoundException(e.message ?: e::class.qualifiedName!!)
            }
        }
    }
}
