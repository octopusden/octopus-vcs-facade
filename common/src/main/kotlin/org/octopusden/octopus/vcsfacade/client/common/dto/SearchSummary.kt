package org.octopusden.octopus.vcsfacade.client.common.dto

import java.util.Date

data class SearchSummary(
    val branches: SearchBranchesSummary,
    val commits: SearchCommitsSummary,
    val pullRequests: SearchPullRequestsSummary
) {
    data class SearchBranchesSummary(val size: Int, val updated: Date?)
    data class SearchCommitsSummary(val size: Int, val latest: Date?)
    data class SearchPullRequestsSummary(val size: Int, val updated: Date?, val status: PullRequestStatus?)
}