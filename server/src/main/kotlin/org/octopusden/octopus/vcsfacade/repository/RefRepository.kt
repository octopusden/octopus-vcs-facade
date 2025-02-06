package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.document.RefDocument
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface RefRepository : CrudRepository<RefDocument, String> {
    fun searchByTypeAndNameContaining(type: RefType, nameToken: String): List<RefDocument>
    fun searchFirst50ByRepositoryIdAndIdAfterOrderByIdAsc(repositoryId: String, id: String): List<RefDocument>
    fun deleteByRepositoryId(repositoryId: String)
}