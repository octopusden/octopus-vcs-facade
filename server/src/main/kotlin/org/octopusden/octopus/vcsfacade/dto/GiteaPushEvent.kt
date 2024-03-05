package org.octopusden.octopus.vcsfacade.dto

import java.util.Date

data class GiteaPushEvent(
    val commits: List<GiteaCommit>,
    val repository: GiteaRepository
) {
    data class GiteaCommit(
        val id: String,
        val message: String,
        val timestamp: Date
    )
}
