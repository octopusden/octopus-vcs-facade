package org.octopusden.octopus.vcsfacade.dto

data class RepositoryResponse<T>(
    val data: List<T>,
)
