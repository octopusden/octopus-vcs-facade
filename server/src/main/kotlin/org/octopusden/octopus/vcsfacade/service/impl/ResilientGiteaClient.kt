package org.octopusden.octopus.vcsfacade.service.impl

import com.fasterxml.jackson.core.io.JsonEOFException
import feign.FeignException
import feign.codec.DecodeException
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaEntityList
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

// Temporary workaround. Remove this once the fix is released in Gitea.
class ResilientGiteaClient(
    private val delegate: GiteaClient,
) : GiteaClient by delegate {
    private val warnedRepositories = ConcurrentHashMap.newKeySet<String>()

    override fun getCommits(
        organization: String,
        repository: String,
        requestParams: Map<String, Any>,
    ): GiteaEntityList<GiteaCommit> {
        if (!requestParams.isFilesRequested()) {
            return delegate.getCommits(organization, repository, requestParams)
        }
        return try {
            val page = delegate.getCommits(organization, repository, requestParams)
            if (page.isSuspiciousEmptyPage()) {
                fallbackPage(organization, repository, requestParams, "empty page")
            } else {
                page
            }
        } catch (e: FeignException) {
            if (!e.isBrokenJsonResponse()) {
                throw e
            }
            fallbackPage(organization, repository, requestParams, e.causeName())
        }
    }

    override fun getCommit(
        organization: String,
        repository: String,
        sha: String,
        requestParams: Map<String, Any>,
    ): GiteaCommit {
        if (!requestParams.isFilesRequested()) {
            return delegate.getCommit(organization, repository, sha, requestParams)
        }
        return try {
            delegate.getCommit(organization, repository, sha, requestParams)
        } catch (e: FeignException) {
            if (!e.isBrokenJsonResponse()) {
                throw e
            }
            val commit = delegate.getCommit(organization, repository, sha, requestParams + ("files" to false))
            logBrokenCommit(organization, repository, sha, e)
            commit.copy(files = emptyList())
        }
    }

    private fun fallbackPage(
        organization: String,
        repository: String,
        requestParams: Map<String, Any>,
        reason: String,
    ): GiteaEntityList<GiteaCommit> {
        val page = delegate.getCommits(organization, repository, requestParams + ("files" to false))
        logBrokenPage(organization, repository, requestParams, page.values.size, reason)
        return GiteaEntityList(
            page.hasMore,
            page.values.map { commit -> commit.copy(files = emptyList()) },
        )
    }

    private fun logBrokenPage(
        organization: String,
        repository: String,
        requestParams: Map<String, Any>,
        commitsCount: Int,
        reason: String,
    ) {
        val repoKey = "$organization/$repository"
        if (warnedRepositories.add(repoKey)) {
            log.warn(
                "Gitea returned broken JSON for {}/{} page {} of {} ({}); {} commits indexed without files.",
                organization,
                repository,
                requestParams["page"],
                requestParams["sha"],
                reason,
                commitsCount,
            )
        } else {
            log.debug(
                "Gitea returned broken JSON for {}/{} page {} of {} ({}); {} commits indexed without files",
                organization,
                repository,
                requestParams["page"],
                requestParams["sha"],
                reason,
                commitsCount,
            )
        }
    }

    private fun logBrokenCommit(
        organization: String,
        repository: String,
        sha: String,
        e: Throwable,
    ) {
        val repoKey = "$organization/$repository"
        if (warnedRepositories.add(repoKey)) {
            log.warn(
                "Gitea returned broken JSON for {}/{} commit {} ({}); commit indexed without files.",
                organization,
                repository,
                sha,
                e.causeName(),
            )
        } else {
            log.debug(
                "Gitea returned broken JSON for {}/{} commit {} ({}); commit indexed without files",
                organization,
                repository,
                sha,
                e.causeName(),
            )
        }
    }

    private fun GiteaEntityList<GiteaCommit>.isSuspiciousEmptyPage() = values.isEmpty() && hasMore != false

    private fun Throwable.causeName() = cause?.javaClass?.simpleName ?: javaClass.simpleName

    private fun Map<String, Any>.isFilesRequested() = this["files"]?.toString()?.toBoolean() ?: true

    private fun Throwable.isBrokenJsonResponse() =
        generateSequence(this) { it.cause }.any {
            it is JsonEOFException ||
                it.message?.contains("Unexpected end-of-input") == true ||
                (it is DecodeException && it.status() in 200..299)
        }

    companion object {
        private val log = LoggerFactory.getLogger(ResilientGiteaClient::class.java)
    }
}
