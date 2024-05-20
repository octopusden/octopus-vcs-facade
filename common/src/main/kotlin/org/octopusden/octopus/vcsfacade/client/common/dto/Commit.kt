package org.octopusden.octopus.vcsfacade.client.common.dto

import java.util.Date

data class Commit(
    val hash: String,
    val message: String,
    val date: Date,
    val author: User,
    val parents: List<String>,
    val link: String,
    val repository: Repository
) : VcsFacadeResponse, Comparable<Commit> {
    override fun compareTo(other: Commit) =
        compareBy(Commit::repository).thenByDescending(Commit::date).compare(this, other)
}
