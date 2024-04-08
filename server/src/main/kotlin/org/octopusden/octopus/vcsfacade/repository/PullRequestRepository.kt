package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.PullRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface PullRequestRepository : CrudRepository<PullRequest, String> {
    fun searchByTitleContainingOrDescriptionContaining(titleToken: String, descriptionToken: String): List<PullRequest>
    fun searchFirst100ByRepositoryIdAndIndexAfterOrderByIndexAsc(repositoryId: String, index: Long): List<PullRequest>
    fun deleteByRepositoryId(repositoryId: String)
}