package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.CommitDocument
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface CommitRepository : CrudRepository<CommitDocument, String> {
    fun searchByMessageContaining(messageToken: String): List<CommitDocument>
    fun searchFirst50ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId: String, id: String): List<CommitDocument>
    fun deleteByRepositoryId(repositoryId: String)
}