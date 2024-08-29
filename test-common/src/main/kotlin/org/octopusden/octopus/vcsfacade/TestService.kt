package org.octopusden.octopus.vcsfacade

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.FileChange
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestReviewer
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.dto.User

sealed class TestService(
    protected val vcsFacadeHost: String,
    vcsHost: String,
    vcsExternalHost: String?
) {
    protected abstract val type: String

    abstract fun sshUrl(group: String, repository: String): String

    protected val vcsEffectiveHost = vcsExternalHost ?: vcsHost

    fun getCommits(resource: String): List<Commit> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<Commit>>() {}
    ).map { it.replaceHost(vcsEffectiveHost) }


    fun getCommitsWithFiles(resource: String): List<CommitWithFiles> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<CommitWithFiles>>() {}
    ).map { it.replaceHost(vcsEffectiveHost) }

    fun getCommit(resource: String): Commit = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<Commit>() {}
    ).replaceHost(vcsEffectiveHost)

    fun getCommitWithFiles(resource: String): CommitWithFiles = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<CommitWithFiles>() {}
    ).replaceHost(vcsEffectiveHost)


    fun getIssuesFromCommits(resource: String): List<String> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<String>>() {}
    )

    fun getTags(resource: String): List<Tag> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<Tag>>() {}
    ).map { it.replaceHost(vcsEffectiveHost) }

    fun getTag(resource: String): Tag = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<Tag>() {}
    ).replaceHost(vcsEffectiveHost)

    fun getSearchIssueInRangesResponse(resource: String): SearchIssueInRangesResponse = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<SearchIssueInRangesResponse>() {}
    ).replaceHost(vcsEffectiveHost)

    fun getSearchSummary(resource: String): SearchSummary = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<SearchSummary>() {}
    )

    fun getBranches(resource: String): List<Branch> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<Branch>>() {}
    ).map { it.replaceHost(vcsEffectiveHost) }

    fun getPullRequests(resource: String): List<PullRequest> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<PullRequest>>() {}
    ).map { it.replaceHost(vcsEffectiveHost) }

    class Bitbucket(
        vcsFacadeHost: String, vcsHost: String, vcsExternalHost: String? = null
    ) : TestService(vcsFacadeHost, vcsHost, vcsExternalHost) {
        override val type = "bitbucket"

        override fun sshUrl(group: String, repository: String) =
            "ssh://git@$vcsEffectiveHost/$group/$repository.git"
    }

    class Gitea(
        vcsFacadeHost: String, vcsHost: String, vcsExternalHost: String? = null, private val useColon: Boolean = false
    ) : TestService(vcsFacadeHost, vcsHost, vcsExternalHost) {
        override val type = "gitea"

        override fun sshUrl(group: String, repository: String) =
            "ssh://git@$vcsEffectiveHost${if (useColon) ":" else "/"}$group/$repository.git"

        fun scan(group: String, repository: String) {
            val query = "sshUrl=${URLEncoder.encode(sshUrl(group, repository), StandardCharsets.UTF_8)}"
            val url = URI("http://$vcsFacadeHost/rest/api/1/indexer/gitea/scan?$query").toURL()
            with(url.openConnection() as HttpURLConnection) {
                setRequestMethod("POST")
                if (getResponseCode() / 100 != 2) {
                    throw RuntimeException("Unable to schedule '$group:$repository' scan")
                }
            }
            Thread.sleep(10000) //vcs-facade.vcs.gitea.index.scan.delay * 2
        }
    }

    companion object {
        private val OBJECT_MAPPER = ObjectMapper().registerKotlinModule()

        private fun String.replaceHost(to: String) = replace("@test.vcs-host@", to, false)

        private fun User.replaceHost(to: String) = User(
            name, avatar?.replaceHost(to)
        )

        private fun Repository.replaceHost(to: String) = Repository(
            sshUrl.replaceHost(to), link.replaceHost(to), avatar?.replaceHost(to)
        )

        private fun Commit.replaceHost(to: String) = Commit(
            hash,
            message,
            date,
            author.replaceHost(to),
            parents,
            link.replaceHost(to),
            repository.replaceHost(to)
        )

        private fun FileChange.replaceHost(to: String) = FileChange(
            type, path, link.replaceHost(to)
        )

        private fun CommitWithFiles.replaceHost(to: String) = CommitWithFiles(
            commit.replaceHost(to), totalFiles, files.map { it.replaceHost(to) }
        )

        private fun Tag.replaceHost(to: String) = Tag(
            name, hash, link.replaceHost(to), repository.replaceHost(to)
        )

        private fun SearchIssueInRangesResponse.replaceHost(to: String) = SearchIssueInRangesResponse(
            issueRanges.mapValues { issueRange ->
                issueRange.value.map {
                    RepositoryRange(it.sshUrl.replaceHost(to), it.fromHashOrRef, it.fromDate, it.toHashOrRef)
                }.toSet()
            }
        )

        private fun Branch.replaceHost(to: String) = Branch(
            name, hash, link.replaceHost(to), repository.replaceHost(to)
        )

        private fun PullRequest.replaceHost(to: String) = PullRequest(
            index,
            title,
            description,
            author.replaceHost(to),
            source,
            target,
            assignees.map { it.replaceHost(to) },
            reviewers.map { PullRequestReviewer(it.user.replaceHost(to), it.approved) },
            status,
            createdAt,
            updatedAt,
            link.replaceHost(to),
            repository.replaceHost(to)
        )
    }
}