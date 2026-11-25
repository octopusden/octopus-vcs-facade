package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryState
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryStatus

/**
 * A commit as the root walk sees it, whichever provider it came from.
 *
 * @property parents the parent ids, first parent first
 */
data class WalkCommit(
    val id: String,
    val parents: List<String>,
    val message: String,
)

/**
 * Finds the root commit of a history: the end of the first-parent chain from the head.
 */
object RootCommitWalk {
    /**
     * @param pages the history in pages, newest first, the head first of all; read lazily, and no page is
     *   requested once the chain has ended
     * @return the root commit, or `null` when the history is empty
     * @throws IllegalStateException when the pages end before the chain does
     */
    fun find(pages: Sequence<List<WalkCommit>>): WalkCommit? {
        val iterator = pages.iterator()
        if (!iterator.hasNext()) return null
        val first = iterator.next()
        val head = first.firstOrNull() ?: return null
        val known = HashMap<String, WalkCommit>()
        first.forEach { known.putIfAbsent(it.id, it) }
        val visited = HashSet<String>()
        var current = head
        while (true) {
            check(visited.add(current.id)) { "Commit '${current.id}' is its own ancestor" }
            val parentId = current.parents.firstOrNull() ?: return current
            while (parentId !in known) {
                check(iterator.hasNext()) { "The history ended before parent '$parentId' of '${current.id}' was found" }
                val page = iterator.next()
                check(page.isNotEmpty()) { "The history ended before parent '$parentId' of '${current.id}' was found" }
                page.forEach { known.putIfAbsent(it.id, it) }
            }
            current = known.getValue(parentId)
        }
    }
}

/**
 * The state of a repository the provider found.
 *
 * @param description the repository description, verbatim; empty counts as none
 * @param root the root commit of the default branch, `null` when there are no commits
 */
fun presentRepositoryState(
    description: String?,
    defaultBranch: String?,
    root: WalkCommit?,
) = RepositoryState(
    RepositoryStatus.PRESENT,
    marker = description?.takeIf { it.isNotEmpty() },
    defaultBranch = defaultBranch,
    hasCommits = root != null,
    rootCommitMarker = root?.let { ProvisioningMarker.parse(it.message) },
)
