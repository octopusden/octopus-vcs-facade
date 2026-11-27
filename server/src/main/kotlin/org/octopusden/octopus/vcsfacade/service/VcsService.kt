package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.VcsProperties
import org.octopusden.octopus.vcsfacade.dto.HashOrRefOrDate
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import java.util.Date

abstract class VcsService(
    vcsServiceProperties: VcsProperties.Service,
) {
    val id = vcsServiceProperties.id.lowercase()
    val type = vcsServiceProperties.type
    val indexing = vcsServiceProperties.indexing
    private val provisioning = vcsServiceProperties.provisioning?.takeIf { it.hasCredential }
    private val provisioningAllowedGroups = provisioning
        ?.allowedGroups
        .orEmpty()
        .map { it.lowercase() }
        .toSet()
    protected val httpUrl = httpUrlOf(vcsServiceProperties)
    protected val sshUrl = vcsServiceProperties.sshUrl.lowercase().trimEnd(':', '/')
    private val sshUrlRegex = "${Regex.escape(
        sshUrl,
    )}[:/]${if (type == VcsServiceType.BITBUCKET) "(?:scm/)?" else ""}((?:[^/]+/)+)([^/]+)\\.git".toRegex()

    /**
     * Whether provisioning may touch [group]: the service has a provisioning credential and lists the group.
     * [parse] lowercases, so the comparison ignores case (Bitbucket project keys are upper case).
     */
    fun isProvisioningAllowed(group: String) = provisioning != null && group.lowercase() in provisioningAllowedGroups

    fun isSupported(sshUrl: String) = sshUrlRegex.matches(sshUrl.lowercase())

    fun parse(sshUrl: String) =
        sshUrlRegex.find(sshUrl.lowercase())!!.destructured.let {
            it.component1().trimEnd('/') to it.component2()
        }

    abstract fun getRepositories(): Sequence<Repository>

    /**
     * Whether the Bitbucket project or Gitea organisation exists, as the provisioning credential sees it.
     * Only the provider's own "not found" means `false`; any other failure propagates.
     */
    abstract fun groupExists(group: String): Boolean

    abstract fun getRepositoryState(
        group: String,
        repository: String,
    ): RepositoryState

    abstract fun findRepository(
        group: String,
        repository: String,
    ): Repository?

    abstract fun getBranches(
        group: String,
        repository: String,
    ): Sequence<Branch>

    abstract fun getTags(
        group: String,
        repository: String,
    ): Sequence<Tag>

    abstract fun createTag(
        group: String,
        repository: String,
        createTag: CreateTag,
    ): Tag

    abstract fun getTag(
        group: String,
        repository: String,
        name: String,
    ): Tag

    abstract fun deleteTag(
        group: String,
        repository: String,
        name: String,
    )

    abstract fun getCommits(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String,
    ): Sequence<Commit>

    abstract fun getCommitsWithFiles(
        group: String,
        repository: String,
        from: HashOrRefOrDate<String, Date>?,
        toHashOrRef: String,
    ): Sequence<CommitWithFiles>

    abstract fun getBranchesCommitGraph(
        group: String,
        repository: String,
    ): Sequence<CommitWithFiles>

    abstract fun getCommit(
        group: String,
        repository: String,
        hashOrRef: String,
    ): Commit

    abstract fun getCommitWithFiles(
        group: String,
        repository: String,
        hashOrRef: String,
    ): CommitWithFiles

    abstract fun getPullRequests(
        group: String,
        repository: String,
    ): Sequence<PullRequest>

    abstract fun createPullRequest(
        group: String,
        repository: String,
        createPullRequest: CreatePullRequest,
    ): PullRequest

    abstract fun getPullRequest(
        group: String,
        repository: String,
        index: Long,
    ): PullRequest

    abstract fun findTags(
        group: String,
        repository: String,
        names: Set<String>,
    ): Sequence<Tag>

    abstract fun findCommits(
        group: String,
        repository: String,
        hashes: Set<String>,
    ): Sequence<Commit>

    abstract fun findPullRequests(
        group: String,
        repository: String,
        indexes: Set<Long>,
    ): Sequence<PullRequest>

    abstract fun findBranches(issueKey: String): Sequence<Branch>

    abstract fun findCommits(issueKey: String): Sequence<Commit>

    abstract fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles>

    abstract fun findPullRequests(issueKey: String): Sequence<PullRequest>

    companion object {
        /**
         * The API base URL as the services use it.
         */
        fun httpUrlOf(vcsServiceProperties: VcsProperties.Service) = vcsServiceProperties.httpUrl.lowercase().trimEnd('/')
    }
}
