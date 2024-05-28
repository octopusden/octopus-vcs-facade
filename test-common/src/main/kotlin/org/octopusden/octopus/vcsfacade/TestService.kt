package org.octopusden.octopus.vcsfacade

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository

sealed class TestService(
    private val host: String,
    private val externalHost: String?
) {
    abstract val type: String

    abstract fun sshUrl(group: String, repository: String): String

    protected val effectiveHost = externalHost ?: host

    fun getCommits(resource: String): List<Commit> =
        OBJECT_MAPPER.readValue(
            TestService::class.java.classLoader.getResourceAsStream("$type/$resource"),
            object : TypeReference<List<Commit>>() {}
        ).let { commits ->
            if (externalHost.isNullOrBlank()) commits else commits.map { it.replaceHost(host, externalHost) }
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
    }

    class Gitlab(
        host: String, externalHost: String? = null
    ) : TestService(host, externalHost) {
        override val type = "gitlab"

        override fun sshUrl(group: String, repository: String) =
            "ssh://git@$effectiveHost:$group/$repository.git"
    }

    companion object {
        private val OBJECT_MAPPER = ObjectMapper().registerKotlinModule()

        private fun Repository.replaceHost(from: String, to: String) = Repository(
            sshUrl.replace(from, to), link.replace(from, to), avatar
        )

        private fun Commit.replaceHost(from: String, to: String) = Commit(
            hash, message, date, author, parents, link.replace(from, to), repository.replaceHost(from, to)
        )
    }
}