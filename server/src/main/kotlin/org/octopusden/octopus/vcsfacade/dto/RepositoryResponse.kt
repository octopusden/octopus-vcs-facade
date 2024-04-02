package org.octopusden.octopus.vcsfacade.dto

import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeResponse

data class RepositoryResponse<T>(val data: List<T>) : VcsFacadeResponse