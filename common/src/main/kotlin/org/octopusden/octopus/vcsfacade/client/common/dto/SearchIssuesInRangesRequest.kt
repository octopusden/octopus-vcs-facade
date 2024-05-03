package org.octopusden.octopus.vcsfacade.client.common.dto

data class SearchIssuesInRangesRequest(val issueKeys: Set<String>, val ranges: Set<RepositoryRange>)
