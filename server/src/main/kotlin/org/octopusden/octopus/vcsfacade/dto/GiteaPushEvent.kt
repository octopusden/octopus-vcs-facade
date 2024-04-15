package org.octopusden.octopus.vcsfacade.dto

import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository

data class GiteaPushEvent(
    val commits: List<GiteaBranch.PayloadCommit>,
    val repository: GiteaRepository
)
