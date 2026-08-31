package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchSummary(
    val branches: SearchBranchesSummary,
    val commits: SearchCommitsSummary,
    val pullRequests: SearchPullRequestsSummary,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchBranchesSummary(
        val size: Int,
        val updated: Date?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchCommitsSummary(
        val size: Int,
        val latest: Date?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchPullRequestsSummary(
        val size: Int,
        val updated: Date?,
        val status: PullRequestStatus?,
    )
}
