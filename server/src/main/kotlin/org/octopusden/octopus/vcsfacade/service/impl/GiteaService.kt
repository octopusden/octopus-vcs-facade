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
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaUser
import org.octopusden.octopus.infrastructure.gitea.client.getBranches
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import org.octopusden.octopus.infrastructure.gitea.client.getPullRequests
import org.octopusden.octopus.infrastructure.gitea.client.getTags
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
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException as GiteaNotFoundException

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
        override fun getApiUrl() = httpUrl

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

    override val sshUrlRegex = "(?:ssh://)?git@$host[:/]([^:/]+)/([^:/]+).git".toRegex()

    private fun getRepository(group: String, repository: String) =
        execute("getRepository($group, $repository)") {
            client.getRepository(group, repository)
        }.toRepository()

    override fun getBranches(group: String, repository: String) = with(getRepository(group, repository)) {
        execute("getBranches($group, $repository)") {
            client.getBranches(group, repository)
        }.map { it.toBranch(this) }
    }

    override fun getTags(group: String, repository: String) = with(getRepository(group, repository)) {
        execute("getTags($group, $repository)") {
            client.getTags(group, repository)
        }.map { it.toTag(this) }
    }

    override fun getCommits(group: String, repository: String, toId: String, fromId: String) =
        with(getRepository(group, repository)) {
            execute("getCommits($group, $repository, $toId, $fromId)") {
                client.getCommits(group, repository, toId, fromId)
            }.map { it.toCommit(this) }
        }

    override fun getCommits(group: String, repository: String, toId: String, fromDate: Date?) =
        with(getRepository(group, repository)) {
            execute("getCommits($group, $repository, $toId, $fromDate)") {
                client.getCommits(group, repository, toId, fromDate)
            }.map { it.toCommit(this) }
        }

    override fun getCommit(group: String, repository: String, id: String) =
        execute("getCommit($group, $repository, $id)") {
            client.getCommit(group, repository, id)
        }.toCommit(getRepository(group, repository))

    fun getPullRequests(group: String, repository: String) = //TODO: expose to VCSClient as abstract method?
        with(getRepository(group, repository)) {
            execute("getPullRequests($group, $repository)") {
                client.getPullRequests(group, repository)
            }.map { it.toPullRequest(this) }
        }

    override fun createPullRequest(group: String, repository: String, createPullRequest: CreatePullRequest) =
        execute("createPullRequest($group, $repository, $createPullRequest)") {
            client.createPullRequestWithDefaultReviewers(
                group,
                repository,
                createPullRequest.sourceBranch,
                createPullRequest.targetBranch,
                createPullRequest.title,
                createPullRequest.description
            )
        }.toPullRequest(getRepository(group, repository))

    override fun getPullRequest(group: String, repository: String, index: Long) =
        execute("getPullRequest($group, $repository, $index)") {
            client.getPullRequest(group, repository, index)
        }.toPullRequest(getRepository(group, repository))

    override fun findCommits(group: String, repository: String, ids: Set<String>) =
        with(getRepository(group, repository)) {
            ids.mapNotNull {
                try {
                    client.getCommit(group, repository, it).toCommit(this)
                } catch (e: GiteaNotFoundException) {
                    null
                }
            }
        }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>) =
        with(getRepository(group, repository)) {
            indexes.mapNotNull {
                try {
                    client.getPullRequest(group, repository, it).toPullRequest(this)
                } catch (e: GiteaNotFoundException) {
                    null
                }
            }
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

    private fun GiteaRepository.toRepository() =
        Repository("ssh://git@$host/$fullName.git", "$httpUrl/$fullName", avatarUrl)

    private fun GiteaBranch.toBranch(repository: Repository) = Branch(
        name, commit.id, "${repository.link}/src/branch/$name", repository
    )

    private fun GiteaTag.toTag(repository: Repository) = Tag(
        name, commit.sha, "${repository.link}/src/tag/$name", repository
    )

    private fun GiteaUser.toUser() = User(username, avatarUrl)

    private fun GiteaCommit.toCommit(repository: Repository) = Commit(
        sha,
        commit.message,
        created,
        author?.toUser() ?: User(commit.author.name, ""),
        parents.map { it.sha },
        "${repository.link}/commit/$sha",
        repository
    )

    private fun GiteaPullRequest.toPullRequest(repository: Repository) = PullRequest(
        number,
        title,
        body,
        user.toUser(),
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
        "${repository.link}/pulls/$number",
        repository
    )

    companion object {
        private val log = LoggerFactory.getLogger(GiteaService::class.java)

        private fun <T> execute(errorMessage: String, clientFunction: () -> T): T {
            try {
                return clientFunction.invoke()
            } catch (e: GiteaNotFoundException) {
                log.error("$errorMessage: ${e.message}")
                throw NotFoundException(e.message ?: e::class.qualifiedName!!)
            }
        }
    }
}
