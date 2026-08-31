package org.octopusden.octopus.vcsfacade.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaBranch
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository

@JsonIgnoreProperties(ignoreUnknown = true)
data class GiteaPushEvent(
    val commits: List<GiteaBranch.PayloadCommit>,
    val repository: GiteaRepository,
)
