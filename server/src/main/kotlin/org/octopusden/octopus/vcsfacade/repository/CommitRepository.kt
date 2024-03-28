package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.Commit
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "opensearch",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
interface CommitRepository : CrudRepository<Commit, String> {
    fun findByMessageContaining(messageToken: String): List<Commit>
    fun findByRepositoryId(repositoryId: String): List<Commit>
    fun deleteByRepositoryId(repositoryId: String)
}