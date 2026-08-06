package org.octopusden.octopus.vcsfacade.client.common.dto

data class Repository(
    val sshUrl: String,
    val link: String,
    val avatar: String? = null,
    // null means "not checked in this context" — the OpenSearch index and the Bitbucket
    // nested-reference shortcut (toBranch/toTag/toCommit/toPullRequest) never fetch this from
    // the provider, so they must not claim a definite false. Only getRepository/getRepositories
    // populate a real value, since those are the only calls that actually ask Bitbucket/Gitea.
    val archived: Boolean? = null,
) : Comparable<Repository> {
    override fun compareTo(other: Repository) = sshUrl compareTo other.sshUrl
}
