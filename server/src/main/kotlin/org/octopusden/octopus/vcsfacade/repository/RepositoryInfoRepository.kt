package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface RepositoryInfoRepository : CrudRepository<RepositoryInfoDocument, String> {
    fun searchFirst1000ByRepositoryTypeAndIdAfterOrderByIdAsc(type: VcsServiceType, id: String): List<RepositoryInfoDocument>
    fun findByRepositoryId(repositoryId: String): RepositoryInfoDocument?
}