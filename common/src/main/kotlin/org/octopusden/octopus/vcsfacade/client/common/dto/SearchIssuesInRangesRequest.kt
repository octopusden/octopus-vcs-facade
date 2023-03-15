package org.octopusden.octopus.vcsfacade.client.common.dto

data class SearchIssuesInRangesRequest(val issues: Set<String>, val ranges: Set<RepositoryRange>)
