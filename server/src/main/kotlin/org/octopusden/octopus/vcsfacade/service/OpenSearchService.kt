package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.document.Commit
import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.octopusden.octopus.vcsfacade.document.Ref
import org.octopusden.octopus.vcsfacade.document.Repository
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent

interface OpenSearchService { //TODO: split to indexer and search services?
    fun registerGiteaCreateRefEvent(giteaCreateRefEvent: GiteaCreateRefEvent)
    fun registerGiteaDeleteRefEvent(giteaDeleteRefEvent: GiteaDeleteRefEvent)
    fun registerGiteaPushEvent(giteaPushEvent: GiteaPushEvent)
    fun registerGiteaPullRequestEvent(giteaPullRequestEvent: GiteaPullRequestEvent)
    fun findBranches(issueKey: String): Map<Repository, List<Ref>>
    fun findCommits(issueKey: String): Map<Repository, List<Commit>>
    fun findPullRequests(issueKey: String): Map<Repository, List<PullRequest>>
    fun find(issueKey: String): SearchSummary
}