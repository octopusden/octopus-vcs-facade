package org.octopusden.octopus.vcsfacade.service.impl

import org.octopusden.octopus.infrastructure.gitea.client.ENTITY_LIMIT
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClient
import org.octopusden.octopus.vcsfacade.service.WalkCommit

/**
 * The commits reachable from [branch] (the provider's default when `null`), newest first, one provider page
 * after another; a page is requested only when the consumer asks for it.
 */
internal fun giteaCommitPages(
    client: GiteaClient,
    group: String,
    repository: String,
    branch: String?,
) = sequence {
    val parameters = mutableMapOf<String, Any>("limit" to ENTITY_LIMIT, "stat" to false, "verification" to false, "files" to false)
    branch?.let { parameters["sha"] = it }
    var page = 1
    do {
        val commits = client.getCommits(group, repository, parameters + mapOf("page" to page++))
        yield(commits.values.map { WalkCommit(it.sha, it.parents.map { parent -> parent.sha }, it.commit.message) })
    } while (commits.hasMore ?: commits.values.isNotEmpty())
}
