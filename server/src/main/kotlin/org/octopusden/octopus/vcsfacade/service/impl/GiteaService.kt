package org.octopusden.octopus.vcsfacade.service.impl

import java.util.Date
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.CredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClassicClient
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.createPullRequestWithDefaultReviewers
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaPullRequest
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaUser
import org.octopusden.octopus.infrastructure.gitea.client.getBranches
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import org.octopusden.octopus.infrastructure.gitea.client.getTags
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

@Service
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitea",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class GiteaService(
    giteaProperties: VCSConfig.GiteaProperties
) : VCSClient(giteaProperties, VcsServiceType.GITEA) {
    private val client: GiteaClient = GiteaClassicClient(object : ClientParametersProvider {
        override fun getApiUrl() = url

        override fun getAuth(): CredentialProvider {
            val authException by lazy {
                IllegalStateException("Auth Token or username/password must be specified for Bitbucket access")
            }
            return giteaProperties.token?.let { StandardBearerTokenCredentialProvider(it) }
                ?: StandardBasicCredCredentialProvider(
                    giteaProperties.username ?: throw authException, giteaProperties.password ?: throw authException
                )
        }
    })

    override val vcsPathRegex = "(?:ssh://)?git@$host[:/]([^:/]+)/([^:/]+).git".toRegex()

    override fun getBranches(group: String, repository: String) =
        execute("getBranches($group, $repository)") {
            client.getBranches(group, repository)
        }.map { it.toBranch(group, repository) }

    override fun getTags(group: String, repository: String) =
        execute("getTags($group, $repository)") {
            client.getTags(group, repository)
        }.map { it.toTag(group, repository) }

    override fun getCommits(group: String, repository: String, toId: String, fromId: String) =
        execute("getCommits($group, $repository, $toId, $fromId)") {
            client.getCommits(group, repository, toId, fromId)
        }.map { it.toCommit(group, repository) }

    override fun getCommits(group: String, repository: String, toId: String, fromDate: Date?) =
        execute("getCommits($group, $repository, $toId, $fromDate)") {
            client.getCommits(group, repository, toId, fromDate)
        }.map { it.toCommit(group, repository) }

    override fun getCommit(group: String, repository: String, commitIdOrRef: String) =
        execute("getCommit($group, $repository, $commitIdOrRef)") {
            client.getCommit(group, repository, commitIdOrRef)
        }.toCommit(group, repository)

    override fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest) =
        execute("createPullRequest($group, $repository, $createPullRequest)") {
            client.createPullRequestWithDefaultReviewers(
                group,
                repository,
                createPullRequest.sourceBranch,
                createPullRequest.targetBranch,
                createPullRequest.title,
                createPullRequest.description
            ).toPullRequestResponse(group, repository)
        }

    override fun getPullRequest(group: String, repository: String, index: Long) =
        execute("getPullRequest($group, $repository, $index)") {
            client.getPullRequest(group, repository, index).toPullRequestResponse(group, repository)
        }

    override fun findBranches(issueKey: String): List<Branch> {
        log.warn("There is no native implementation of findBranches for $vcsServiceType")
        return emptyList()
    }

    override fun findCommits(issueKey: String): List<Commit> {
        log.warn("There is no native implementation of findCommits for $vcsServiceType")
        return emptyList()
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.warn("There is no native implementation of findPullRequests for $vcsServiceType")
        return emptyList()
    }

    private fun getVcsUrl(organization: String, repository: String) = "ssh://git@$host/$organization/$repository.git"

    private fun GiteaBranch.toBranch(organization: String, repository: String) = Branch(
        name, commit.id, "$url/$organization/$repository/src/branch/$name", getVcsUrl(organization, repository)
    )

    private fun GiteaTag.toTag(organization: String, repository: String) = Tag(
        name, commit.sha, "$url/$organization/$repository/src/tag/$name", getVcsUrl(organization, repository)
    )

    private fun GiteaCommit.toCommit(organization: String, repository: String) = Commit(
        sha,
        commit.message,
        created,
        author?.username ?: commit.author.name,
        parents.map { it.sha },
        "$url/$organization/$repository/commit/$sha",
        getVcsUrl(organization, repository),
        author?.avatarUrl
    )

    private fun GiteaUser.toUser() = User(username, avatarUrl)

    private fun GiteaPullRequest.toPullRequestResponse(organization: String, repository: String) = PullRequest(
        id,
        title,
        body,
        head.label,
        base.label,
        assignees.map { it.toUser() },
        requestedReviewers.map { it.toUser() },
        if (merged) {
            PullRequestStatus.MERGED
        } else if (state == GiteaPullRequest.GiteaPullRequestState.CLOSED) {
            PullRequestStatus.DECLINED
        } else {
            PullRequestStatus.OPENED
        },
        createdAt,
        updatedAt,
        "$url/$organization/$repository/pulls/$id",
        getVcsUrl(organization, repository)
    )

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
