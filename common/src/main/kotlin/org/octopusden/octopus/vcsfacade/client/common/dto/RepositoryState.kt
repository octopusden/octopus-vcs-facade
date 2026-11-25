package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * What a VCS holds for a repository.
 *
 * @property marker the repository description, verbatim
 * @property defaultBranch the default branch name
 * @property hasCommits whether the repository has any commit
 * @property rootCommitMarker the `Provisioning-Marker` trailer of the root commit (the end of the default branch's first-parent history)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryState(
    val status: RepositoryStatus,
    val marker: String? = null,
    val defaultBranch: String? = null,
    val hasCommits: Boolean = false,
    val rootCommitMarker: String? = null,
)
