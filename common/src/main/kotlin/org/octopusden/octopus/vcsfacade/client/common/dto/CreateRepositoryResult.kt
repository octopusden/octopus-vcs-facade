package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * The answer to a repository create.
 *
 * @property created `true` when this call changed anything (HTTP 201), `false` when the repository was already as requested (HTTP 200)
 * @property state the repository state after the call
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateRepositoryResult(
    val created: Boolean,
    val state: RepositoryState,
)
