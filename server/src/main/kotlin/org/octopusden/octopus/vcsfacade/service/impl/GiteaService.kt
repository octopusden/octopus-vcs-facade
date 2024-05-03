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
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaPullRequestReview
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaTag
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaUser
import org.octopusden.octopus.infrastructure.gitea.client.exception.NotFoundException
import org.octopusden.octopus.infrastructure.gitea.client.getBranches
import org.octopusden.octopus.infrastructure.gitea.client.getBranchesCommitGraph
import org.octopusden.octopus.infrastructure.gitea.client.getCommits
import org.octopusden.octopus.infrastructure.gitea.client.getOrganizations
import org.octopusden.octopus.infrastructure.gitea.client.getPullRequestReviews
import org.octopusden.octopus.infrastructure.gitea.client.getPullRequests
import org.octopusden.octopus.infrastructure.gitea.client.getRepositories
import org.octopusden.octopus.infrastructure.gitea.client.getTags
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
    prefix = "vcs-facade.vcs.gitea", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
class GiteaService(
    giteaProperties: VcsConfig.GiteaProperties
) : VcsService(giteaProperties) {
    private val client: GiteaClient = GiteaClassicClient(object : ClientParametersProvider {
        override fun getApiUrl() = httpUrl

        override fun getAuth(): CredentialProvider {
            val authException by lazy {
                IllegalStateException("Auth Token or username/password must be specified for Gitea access")
            }
            return giteaProperties.token?.let { StandardBearerTokenCredentialProvider(it) }
                ?: StandardBasicCredCredentialProvider(
                    giteaProperties.username ?: throw authException, giteaProperties.password ?: throw authException
                )
        }
    })

    override val sshUrlRegex = "(?:ssh://)?git@$host[:/]([^:/]+)/([^:/]+).git".toRegex()

    override fun getSshUrl(group: String, repository: String) = "ssh://git@$host/$group/$repository.git"

    fun getRepositories(): List<Repository> {
        log.trace("=> getRepositories()")
        return client.getOrganizations().flatMap { client.getRepositories(it.name) }.map { toRepository(it) }.also {
            log.trace("<= getRepositories(): {}", it)
        }
    }

    fun isRepositoryExist(group: String, repository: String): Boolean {
        log.trace("=> isRepositoryExist({}, {})", group, repository)
        return try {
            client.getRepository(group, repository)
            true
        } catch (e: NotFoundException) {
            false
        }.also {
            log.trace("<= isRepositoryExist({}, {}): {}", group, repository, it)
        }
    }

    fun getRepository(group: String, repository: String): Repository {
        log.trace("=> getRepository({}, {})", group, repository)
        return toRepository(client.getRepository(group, repository)).also {
            log.trace("<= getRepository({}, {}): {}", group, repository, it)
        }
    }

    override fun getBranches(group: String, repository: String): List<Branch> {
        log.trace("=> getBranches({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getBranches(group, repository).map { it.toBranch(this) }
        }.also {
            log.trace("<= getBranches({}, {}): {}", group, repository, it)
        }
    }

    override fun getTags(group: String, repository: String): List<Tag> {
        log.trace("=> getTags({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getTags(group, repository).map { it.toTag(this) }
        }.also {
            log.trace("<= getTags({}, {}): {}", group, repository, it)
        }
    }

    override fun getCommits(group: String, repository: String, toHashOrRef: String, fromHashOrRef: String): List<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, toHashOrRef, fromHashOrRef)
        return with(getRepository(group, repository)) {
            client.getCommits(group, repository, toHashOrRef, fromHashOrRef).map { it.toCommit(this) }
        }.also {
            log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, toHashOrRef, fromHashOrRef, it)
        }
    }

    override fun getCommits(group: String, repository: String, toHashOrRef: String, fromDate: Date?): List<Commit> {
        log.trace("=> getCommits({}, {}, {}, {})", group, repository, toHashOrRef, fromDate)
        return with(getRepository(group, repository)) {
            client.getCommits(group, repository, toHashOrRef, fromDate).map { it.toCommit(this) }
        }.also {
            log.trace("<= getCommits({}, {}, {}, {}): {}", group, repository, toHashOrRef, fromDate, it)
        }
    }

    fun getBranchesCommitGraph(group: String, repository: String): List<Commit> {
        log.trace("=> getBranchesCommitGraph({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getBranchesCommitGraph(group, repository).map { it.toCommit(this) }
        }.also {
            log.trace("<= getBranchesCommitGraph({}, {}): {}", group, repository, it)
        }
    }

    override fun getCommit(group: String, repository: String, hashOrRef: String): Commit {
        log.trace("=> getCommit({}, {}, {})", group, repository, hashOrRef)
        return client.getCommit(group, repository, hashOrRef).toCommit(getRepository(group, repository)).also {
            log.trace("<= getCommit({}, {}, {}): {}", group, repository, hashOrRef, it)
        }
    }

    fun getPullRequestReviews(group: String, repository: String, number: Long): List<GiteaPullRequestReview> {
        log.trace("=> getPullRequestReviews({}, {}, {})", group, repository, number)
        return client.getPullRequestReviews(group, repository, number).also {
            log.trace("<= getPullRequestReviews({}, {}, {}): {}", group, repository, number, it)
        }
    }

    fun getPullRequests(group: String, repository: String): List<PullRequest> {
        log.trace("=> getPullRequests({}, {})", group, repository)
        return with(getRepository(group, repository)) {
            client.getPullRequests(group, repository).map {
                it.toPullRequest(this, getPullRequestReviews(group, repository, it.number))
            }
        }.also {
            log.trace("<= getPullRequests({}, {}): {}", group, repository, it)
        }
    }

    override fun createPullRequest(
        group: String, repository: String, createPullRequest: CreatePullRequest
    ): PullRequest {
        log.trace("=> getPullRequests({}, {}, {})", group, repository, createPullRequest)
        return client.createPullRequestWithDefaultReviewers(
            group,
            repository,
            createPullRequest.sourceBranch,
            createPullRequest.targetBranch,
            createPullRequest.title,
            createPullRequest.description
        ).let {
            it.toPullRequest(
                getRepository(group, repository), client.getPullRequestReviews(group, repository, it.number)
            )
        }.also {
            log.trace("<= getPullRequests({}, {}, {}): {}", group, repository, createPullRequest, it)
        }
    }

    override fun getPullRequest(group: String, repository: String, index: Long): PullRequest {
        log.trace("=> getPullRequest({}, {}, {})", group, repository, index)
        return client.getPullRequest(group, repository, index).let {
            it.toPullRequest(
                getRepository(group, repository), client.getPullRequestReviews(group, repository, it.number)
            )
        }.also {
            log.trace("<= getPullRequest({}, {}, {}): {}", group, repository, index, it)
        }
    }

    override fun findCommits(group: String, repository: String, hashes: Set<String>): List<Commit> {
        log.trace("=> findCommits({}, {}, {})", group, repository, hashes)
        return with(getRepository(group, repository)) {
            hashes.mapNotNull {
                try {
                    client.getCommit(group, repository, it).toCommit(this)
                } catch (e: NotFoundException) {
                    null
                }
            }
        }.also {
            log.trace("<= findCommits({}, {}, {}): {}", group, repository, hashes, it)
        }
    }

    override fun findPullRequests(group: String, repository: String, indexes: Set<Long>): List<PullRequest> {
        log.trace("=> findPullRequests({}, {}, {})", group, repository, indexes)
        return with(getRepository(group, repository)) {
            indexes.mapNotNull {
                try {
                    client.getPullRequest(group, repository, it)
                        .toPullRequest(this, client.getPullRequestReviews(group, repository, it))
                } catch (e: NotFoundException) {
                    null
                }
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
        log.warn("There is no native implementation of findCommits")
        return emptyList()
    }

    override fun findPullRequests(issueKey: String): List<PullRequest> {
        log.warn("There is no native implementation of findPullRequests")
        return emptyList()
    }

    fun toRepository(giteaRepository: GiteaRepository): Repository {
        val (organization, repository) = giteaRepository.toOrganizationAndRepository()
        return Repository(getSshUrl(organization, repository),
            "$httpUrl/$organization/$repository",
            giteaRepository.avatarUrl.ifBlank { null })
    }

    companion object {
        private val log = LoggerFactory.getLogger(GiteaService::class.java)

        fun GiteaRepository.toOrganizationAndRepository(): Pair<String, String> {
            val repositoryFullNameParts = fullName.lowercase().split("/")
            return repositoryFullNameParts[0] to repositoryFullNameParts[1]
        }

        fun GiteaBranch.toBranch(repository: Repository) = Branch(
            name, commit.id, "${repository.link}/src/branch/$name", repository
        )

        fun GiteaTag.toTag(repository: Repository) = Tag(
            name, commit.sha, "${repository.link}/src/tag/$name", repository
        )

        private fun GiteaUser.toUser() = User(username, avatarUrl.ifBlank { null })

        private fun GiteaCommit.toCommit(repository: Repository) = Commit(
            sha,
            commit.message,
            created,
            author?.toUser() ?: User(commit.author.name),
            parents.map { it.sha },
            "${repository.link}/commit/$sha",
            repository
        )

        fun GiteaPullRequest.toPullRequest(
            repository: Repository, giteaPullRequestReviews: List<GiteaPullRequestReview>
        ): PullRequest {
            val approvedGiteaUserIds = giteaPullRequestReviews.filter {
                it.state == GiteaPullRequestReview.GiteaPullRequestReviewState.APPROVED && !it.dismissed
            }.mapNotNull { it.user?.id }.toSet()
            return PullRequest(
                number,
                title,
                body,
                user.toUser(),
                head.label,
                base.label,
                assignees.map { it.toUser() },
                requestedReviewers.map { PullRequestReviewer(it.toUser(), approvedGiteaUserIds.contains(it.id)) },
                if (merged) {
                    PullRequestStatus.MERGED
                } else if (state == GiteaPullRequest.GiteaPullRequestState.CLOSED) {
                    PullRequestStatus.DECLINED
                } else {
                    PullRequestStatus.OPEN
                },
                createdAt,
                updatedAt,
                "${repository.link}/pulls/$number",
                repository
            )
        }
    }
}
