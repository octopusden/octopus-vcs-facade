package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.client.common.dto.RefType
import org.octopusden.octopus.vcsfacade.document.Ref
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface RefRepository : CrudRepository<Ref, String> {
    fun searchByTypeAndNameContaining(type: RefType, nameToken: String): List<Ref>
    fun searchFirst100ByRepositoryIdAndTypeAndHashAfterOrderByHashAsc(
        repositoryId: String, type: RefType, hash: String
    ): List<Ref>

    fun deleteByRepositoryId(repositoryId: String)
}