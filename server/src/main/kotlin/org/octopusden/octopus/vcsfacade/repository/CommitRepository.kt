package org.octopusden.octopus.vcsfacade.repository

import org.octopusden.octopus.vcsfacade.document.Commit
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.CrudRepository

@ConditionalOnProperty(
    prefix = "opensearch", name = ["enabled"], havingValue = "true", matchIfMissing = true
)
interface CommitRepository : CrudRepository<Commit, String> {
    fun searchByMessageContaining(messageToken: String): List<Commit>
    fun searchFirst100ByRepositoryIdAndHashAfterOrderByHashAsc(repositoryId: String, hash: String): List<Commit>
    fun deleteByRepositoryId(repositoryId: String)
}