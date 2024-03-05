package org.octopusden.octopus.vcsfacade.dto

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.util.Date

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GiteaPullRequestEvent(
    val action: String,
    val pullRequest: GiteaPullRequest,
    val repository: GiteaRepository
) {
    enum class GiteaPullRequestState(
        @get:JsonValue
        val jsonValue: String
    ) {
        OPEN("open"), CLOSED("closed")
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class GiteaPullRequest(
        val number: Long,
        val title: String,
        val body: String,
        val state: GiteaPullRequestState,
        val merged: Boolean,
        val updatedAt: Date
    )
}
