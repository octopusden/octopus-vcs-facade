package org.octopusden.octopus.vcsfacade.service.impl

import com.fasterxml.jackson.core.io.JsonEOFException
import feign.FeignException
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaCommit
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaEntityList
import org.slf4j.LoggerFactory

class ResilientGiteaClient(
    private val delegate: GiteaClient,
) : GiteaClient by delegate {
    override fun getCommits(
        organization: String,
        repository: String,
        requestParams: Map<String, Any>,
    ): GiteaEntityList<GiteaCommit> {
        if (!requestParams.isFilesRequested()) {
            return delegate.getCommits(organization, repository, requestParams)
        }
        return try {
            delegate.getCommits(organization, repository, requestParams)
        } catch (e: FeignException) {
            if (!e.isTruncatedJson()) {
                throw e
            }
            log.warn(
                "Gitea returned truncated JSON for {}/{} page {} of {} with files=true, retrying with files=false",
                organization,
                repository,
                requestParams["page"],
                requestParams["sha"],
            )
            val page = delegate.getCommits(organization, repository, requestParams + ("files" to false))
            GiteaEntityList(
                page.hasMore,
                page.values.map { commit ->
                    commit.copy(files = getFiles(organization, repository, commit.sha))
                },
            )
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
            if (!e.isTruncatedJson()) {
                throw e
            }
            log.warn(
                "Gitea returned truncated JSON for {}/{} commit {} with files=true, indexing without files",
                organization,
                repository,
                sha,
            )
            delegate
                .getCommit(organization, repository, sha, requestParams + ("files" to false))
                .copy(files = emptyList())
        }
    }

    private fun getFiles(
        organization: String,
        repository: String,
        sha: String,
    ): Collection<GiteaCommit.GiteaCommitAffectedFile> =
        getCommit(
            organization,
            repository,
            sha,
            mapOf(
                "stat" to false,
                "verification" to false,
                "files" to true,
            ),
        ).files.orEmpty()

    private fun Map<String, Any>.isFilesRequested() = this["files"]?.toString()?.toBoolean() ?: true

    private fun Throwable.isTruncatedJson() =
        generateSequence(this) { it.cause }.any {
            it is JsonEOFException || it.message?.contains("Unexpected end-of-input") == true
        }

    companion object {
        private val log = LoggerFactory.getLogger(ResilientGiteaClient::class.java)
    }
}
