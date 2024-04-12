package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.PullRequestDocument
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface PullRequestRepository : CrudRepository<PullRequestDocument, String> {
    fun searchByTitleContainingOrDescriptionContaining(titleToken: String, descriptionToken: String): List<PullRequestDocument>
    fun searchFirst1000ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId: String, id: String): List<PullRequestDocument>
    fun deleteByRepositoryId(repositoryId: String)
}