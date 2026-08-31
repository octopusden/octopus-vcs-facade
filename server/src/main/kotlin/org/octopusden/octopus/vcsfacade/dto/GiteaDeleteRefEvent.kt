package org.octopusden.octopus.vcsfacade.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaRepository

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GiteaDeleteRefEvent(
    val refType: GiteaRefType,
    val ref: String,
    val repository: GiteaRepository,
)
