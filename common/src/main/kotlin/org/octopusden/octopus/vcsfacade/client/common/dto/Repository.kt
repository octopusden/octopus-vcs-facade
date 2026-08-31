package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Repository(
    val sshUrl: String,
    val link: String,
    val avatar: String? = null,
    /**
     * Whether the repository is archived, or `null` when the state was not resolved. Treat `null` as unknown, not as "not archived".
     */
    val archived: Boolean? = null,
) : Comparable<Repository> {
    override fun compareTo(other: Repository) = sshUrl compareTo other.sshUrl
}
