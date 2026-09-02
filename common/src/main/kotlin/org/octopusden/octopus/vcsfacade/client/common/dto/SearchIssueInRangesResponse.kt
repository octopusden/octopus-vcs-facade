package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchIssueInRangesResponse(
    val issueRanges: Map<String, Set<RepositoryRange>>,
)
