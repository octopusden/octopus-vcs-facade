package org.octopusden.octopus.vcsfacade.dto

import java.util.Date
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository

data class GiteaPushEvent(
    val commits: List<GiteaPushEventCommit>,
    val repository: GiteaRepository
) {
    //TODO: check if it is the same as org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch.PayloadCommit
    data class GiteaPushEventCommit(
        val id: String,
        val message: String,
        val timestamp: Date
    )
}
