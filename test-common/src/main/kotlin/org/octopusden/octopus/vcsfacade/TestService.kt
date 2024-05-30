package org.octopusden.octopus.vcsfacade

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.FileChange
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.User

sealed class TestService(
    private val host: String,
    private val externalHost: String?
) {
    protected abstract val type: String

    abstract fun sshUrl(group: String, repository: String): String

    protected val effectiveHost = externalHost ?: host

    fun getCommits(resource: String): List<Commit> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<Commit>>() {}
    ).let { commits ->
        if (externalHost.isNullOrBlank()) commits else commits.map { it.replaceHost(host, externalHost) }
    }

    fun getCommitsWithFiles(resource: String): List<CommitWithFiles> = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<List<CommitWithFiles>>() {}
    ).let { commitsWithFiles ->
        if (externalHost.isNullOrBlank()) commitsWithFiles
        else commitsWithFiles.map { it.replaceHost(host, externalHost) }
    }

    fun getCommit(resource: String): Commit = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<Commit>() {}
    ).let { commit ->
        if (externalHost.isNullOrBlank()) commit else commit.replaceHost(host, externalHost)
    }

    fun getCommitWithFiles(resource: String): CommitWithFiles = OBJECT_MAPPER.readValue(
        TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
        object : TypeReference<CommitWithFiles>() {}
    ).let { commitWithFiles ->
        if (externalHost.isNullOrBlank()) commitWithFiles else commitWithFiles.replaceHost(host, externalHost)
    }

    class Bitbucket(
        host: String, externalHost: String? = null
    ) : TestService(host, externalHost) {
        override val type = "bitbucket"

        override fun sshUrl(group: String, repository: String) =
            "ssh://git@$effectiveHost/$group/$repository.git"
    }

    class Gitea(
        host: String, externalHost: String? = null, private val useSlash: Boolean = false
    ) : TestService(host, externalHost) {
        override val type = "gitea"

        override fun sshUrl(group: String, repository: String) =
            "ssh://git@$effectiveHost${if (useSlash) "/" else ":"}$group/$repository.git"

        fun scan(group: String, repository: String) {
            val query = "sshUrl=${URLEncoder.encode(sshUrl(group, repository), StandardCharsets.UTF_8)}"
            val url = URI("$VCS_FACADE_API_URL/rest/api/1/indexer/gitea/scan?$query").toURL()
            with(url.openConnection() as HttpURLConnection) {
                setRequestMethod("POST")
                if (getResponseCode() / 100 != 2) {
                    throw RuntimeException("Unable to schedule '$group:$repository' scan")
                }
            }
            Thread.sleep(10000) //vcs-facade.vcs.gitea.index.scan.delay * 2
        }
    }

    class Gitlab(
        host: String, externalHost: String? = null
    ) : TestService(host, externalHost) {
        override val type = "gitlab"

        override fun sshUrl(group: String, repository: String) =
            "ssh://git@$effectiveHost:$group/$repository.git"
    }

    companion object {
        const val VCS_FACADE_API_URL = "http://localhost:8080" //TODO: use some custom port?

        private val OBJECT_MAPPER = ObjectMapper().registerKotlinModule()

        private fun User.replaceHost(from: String, to: String) = User(
            name, avatar?.replace(from, to)
        )

        private fun Repository.replaceHost(from: String, to: String) = Repository(
            sshUrl.replace(from, to), link.replace(from, to), avatar?.replace(from, to)
        )

        private fun Commit.replaceHost(from: String, to: String) = Commit(
            hash,
            message,
            date,
            author.replaceHost(from, to),
            parents,
            link.replace(from, to),
            repository.replaceHost(from, to)
        )

        private fun FileChange.replaceHost(from: String, to: String) = FileChange(
            type, path, link.replace(from, to)
        )

        private fun CommitWithFiles.replaceHost(from: String, to: String) = CommitWithFiles(
            commit.replaceHost(from, to), totalFiles, files.map { it.replaceHost(from, to) }
        )
    }
}