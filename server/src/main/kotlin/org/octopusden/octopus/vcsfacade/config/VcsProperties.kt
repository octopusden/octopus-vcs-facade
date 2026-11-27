package org.octopusden.octopus.vcsfacade.config

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("vcs-facade.vcs")
data class VcsProperties(
    val services: List<Service>,
) {
    data class Service(
        val id: String,
        val type: VcsServiceType,
        val httpUrl: String,
        val sshUrl: String,
        val token: String?,
        val username: String?,
        val password: String?,
        val healthCheck: HealthCheck?,
        val indexing: Boolean = true,
        val provisioning: Provisioning? = null,
    ) {
        fun getCredentialProvider() =
            credentialProvider(type, token, username, password)
                ?: throw IllegalStateException("Auth token or username/password must be specified")

        /**
         * What provisioning uses on this VCS: a credential that may create and push, and the groups
         * (Bitbucket project keys, Gitea organisations) it may touch.
         */
        data class Provisioning(
            val token: String? = null,
            val username: String? = null,
            val password: String? = null,
            val allowedGroups: Set<String> = emptySet(),
        ) {
            val hasCredential get() = token != null || (username != null && password != null)

            fun getCredentialProvider(type: VcsServiceType) =
                credentialProvider(type, token, username, password)
                    ?: error("Provisioning token or username/password must be specified")
        }

        data class HealthCheck(
            val group: String,
            val repository: String,
            val fromCommit: String,
            val toCommit: String,
            val expectedCommits: Set<String>,
        )
    }
}

private fun credentialProvider(
    type: VcsServiceType,
    token: String?,
    username: String?,
    password: String?,
) = if (token != null) {
    if (type == VcsServiceType.BITBUCKET) {
        BitbucketBearerTokenCredentialProvider(token)
    } else {
        StandardBearerTokenCredentialProvider(token)
    }
} else if (username != null && password != null) {
    if (type == VcsServiceType.BITBUCKET) {
        BitbucketBasicCredentialProvider(username, password)
    } else {
        StandardBasicCredCredentialProvider(username, password)
    }
} else {
    null
}
