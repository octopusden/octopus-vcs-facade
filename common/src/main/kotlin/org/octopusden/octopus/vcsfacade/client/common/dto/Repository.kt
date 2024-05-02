package org.octopusden.octopus.vcsfacade.client.common.dto

data class Repository(
    val sshUrl: String, val link: String, val avatar: String? = null
) : Comparable<Repository> {
    override fun compareTo(other: Repository) = sshUrl compareTo other.sshUrl
}
