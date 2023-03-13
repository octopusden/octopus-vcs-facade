package org.octopusden.vcsfacade.client.common.dto

data class SearchIssuesInRangesRequest(val issues: Set<String>, val ranges: Set<RepositoryRange>)
