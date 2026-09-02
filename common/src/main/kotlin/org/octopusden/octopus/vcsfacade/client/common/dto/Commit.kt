package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class Commit(
    val hash: String,
    val message: String,
    val date: Date,
    val author: User,
    val parents: List<String>,
    val link: String,
    val repository: Repository,
) : Comparable<Commit> {
    override fun compareTo(other: Commit) = compareBy(Commit::repository).thenByDescending(Commit::date).compare(this, other)
}
