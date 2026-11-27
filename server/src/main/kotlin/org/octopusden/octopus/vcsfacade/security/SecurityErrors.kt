package org.octopusden.octopus.vcsfacade.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.VcsFacadeErrorCode
import org.springframework.http.MediaType

/**
 * Writes the usual [ErrorResponse] for the answers Spring Security produces before a controller runs.
 */
fun HttpServletResponse.writeError(
    objectMapper: ObjectMapper,
    status: Int,
    message: String,
) {
    this.status = status
    contentType = MediaType.APPLICATION_JSON_VALUE
    characterEncoding = "UTF-8"
    writer.write(objectMapper.writeValueAsString(ErrorResponse(VcsFacadeErrorCode.OTHER, message)))
}
