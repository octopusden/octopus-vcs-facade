package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
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

@Service
@ConditionalOnProperty(prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class GiteaService(giteaProperties: VCSConfig.GiteaProperties) : VCSClient(giteaProperties) {
    private val client: GiteaClient = GiteaClassicClient(object : ClientParametersProvider {
        override fun getApiUrl() = giteaProperties.host

        override fun getAuth(): CredentialProvider {
            val authException by lazy {
                IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
            }
            return giteaProperties.token?.let { StandardBearerTokenCredentialProvider(it) }
                ?: StandardBasicCredCredentialProvider(
                    giteaProperties.username ?: throw authException,
                    giteaProperties.password ?: throw authException
                )
        }
    })

    override val vcsPathRegex = "ssh://git@$host[:/]([^:/]+)/([^:/]+).git".toRegex()

    private fun String.toOrganizationAndRepository() =
        vcsPathRegex.find(this.lowercase())!!.destructured.let { it.component1() to it.component2() }

    override fun getCommits(vcsPath: String, toId: String, fromId: String): Collection<Commit> {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()
        return execute("getCommits($vcsPath, $toId, $fromId)") {
            client.getCommits(organization, repository, toId, fromId)
        }.map { it.toCommit(vcsPath) }
    }

    override fun getCommits(vcsPath: String, toId: String, fromDate: Date?): Collection<Commit> {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()
        return execute("getCommits($vcsPath, $toId, $fromDate)") {
            client.getCommits(organization, repository, toId, fromDate)
        }.map { it.toCommit(vcsPath) }
    }

    override fun getCommits(issueKey: String): List<Commit> {
        return emptyList()
    }

    override fun getTags(vcsPath: String): List<Tag> {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()
        return execute("getTags($vcsPath)") {
            client.getTags(organization, repository)
        }.map { it.toTag() }
    }

    override fun getCommit(vcsPath: String, commitIdOrRef: String): Commit {
        val (organization, repository) = vcsPath.toOrganizationAndRepository()
        return execute("getCommit($vcsPath, $commitIdOrRef)") {
            client.getCommit(organization, repository, commitIdOrRef)
        }.toCommit(vcsPath)
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

    companion object {
        private val log = LoggerFactory.getLogger(GiteaService::class.java)

        private fun GiteaPullRequest.toPullRequestResponse() = PullRequestResponse(id)

        private fun <T> execute(errorMessage: String, clientFunction: () -> T): T {
            try {
                return clientFunction.invoke()
            } catch (e: org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException) {
                log.error("$errorMessage: ${e.message}")
                throw NotFoundException(e.message ?: e::class.qualifiedName!!)
            }
        }

        private fun GiteaCommit.toCommit(vcsPath: String) =
            Commit(sha, commit.message, commit.author.date, commit.author.name, parents.map { it.sha }, vcsPath)

        private fun GiteaTag.toTag() = Tag(commit.sha, name)
    }
}
