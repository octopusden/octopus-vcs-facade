package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.Repository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "opensearch",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
interface RepositoryRepository : CrudRepository<Repository, String> {

}