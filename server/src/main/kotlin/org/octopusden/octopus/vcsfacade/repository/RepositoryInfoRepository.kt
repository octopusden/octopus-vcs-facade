package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.RepositoryInfoDocument
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "vcs-facade.opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface RepositoryInfoRepository : CrudRepository<RepositoryInfoDocument, String> {
    fun searchFirst50ByIdAfterOrderByIdAsc(id: String): List<RepositoryInfoDocument>
    fun searchFirst50ByScanRequiredAndIdAfterOrderByIdAsc(scanRequired: Boolean, id: String): List<RepositoryInfoDocument>
}