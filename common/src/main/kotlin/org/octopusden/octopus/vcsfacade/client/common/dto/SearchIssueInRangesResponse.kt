package org.octopusden.octopus.vcsfacade.client.common.dto

data class SearchIssueInRangesResponse(val issueRanges: Map<String, Set<RepositoryRange>>)
