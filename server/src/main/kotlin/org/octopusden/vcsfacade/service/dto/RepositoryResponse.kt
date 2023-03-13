package org.octopusden.vcsfacade.service.dto

import org.octopusden.vcsfacade.client.common.dto.VcsFacadeResponse

data class RepositoryResponse<T>(val data: List<T>) : VcsFacadeResponse
