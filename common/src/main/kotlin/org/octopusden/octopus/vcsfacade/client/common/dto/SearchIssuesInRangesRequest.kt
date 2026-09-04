package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchIssuesInRangesRequest(
    val issueKeys: Set<String>,
    val ranges: Set<RepositoryRange>,
)
