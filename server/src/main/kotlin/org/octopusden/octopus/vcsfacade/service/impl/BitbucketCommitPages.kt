package org.octopusden.octopus.vcsfacade.service.impl

import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClient
import org.octopusden.octopus.infrastructure.bitbucket.client.ENTITY_LIMIT
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketCommit
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketEntityList
import org.octopusden.octopus.vcsfacade.service.WalkCommit

/**
 * The commits of the default branch, newest first, one provider page after another; a page is requested
 * only when the consumer asks for it.
 */
internal fun bitbucketCommitPages(
    client: BitbucketClient,
    group: String,
    repository: String,
    firstPage: BitbucketEntityList<BitbucketCommit>,
) = sequence {
    var page: BitbucketEntityList<BitbucketCommit>? = firstPage
    while (page != null) {
        yield(page.values.map { WalkCommit(it.id, it.parents.map { parent -> parent.id }, it.message) })
        val start = page.nextPageStart.takeUnless { page?.isLastPage == true }
        page = start?.let { client.getCommits(group, repository, mapOf("limit" to ENTITY_LIMIT, "start" to it)) }
    }
}
