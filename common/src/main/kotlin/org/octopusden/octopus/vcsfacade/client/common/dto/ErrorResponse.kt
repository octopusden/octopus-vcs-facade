package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorResponse(
    val errorCode: VcsFacadeErrorCode,
    val errorMessage: String,
    /**
     * The repository state a `REPOSITORY_EXISTS` or `REPOSITORY_CONFLICT` answer was decided from.
     */
    val repositoryState: RepositoryState? = null,
)
