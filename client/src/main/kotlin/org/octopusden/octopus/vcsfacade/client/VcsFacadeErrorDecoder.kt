package org.octopusden.octopus.vcsfacade.client

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Response
import feign.codec.ErrorDecoder
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse

class VcsFacadeErrorDecoder(private val objectMapper: ObjectMapper) : ErrorDecoder.Default() {
    override fun decode(methodKey: String?, response: Response?): Exception {
        return getErrorResponse(response)?.let {
            it.errorCode.getException(it.errorMessage)
        } ?: super.decode(methodKey, response)
    }

    private fun getErrorResponse(response: Response?) = if (response != null) {
        if (response.headers()["content-type"]?.find { header -> header.contains("application/json") } != null) {
            try {
                response.body()?.asInputStream()?.use { objectMapper.readValue(it, ErrorResponse::class.java) }
            } catch (e: Exception) {
                null
            }
        } else null
    } else null
}
