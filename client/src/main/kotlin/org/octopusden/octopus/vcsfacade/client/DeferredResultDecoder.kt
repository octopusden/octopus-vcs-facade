package org.octopusden.octopus.vcsfacade.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.RetryResponse
import feign.Response
import feign.RetryableException
import feign.jackson.JacksonDecoder
import org.apache.http.HttpStatus
import java.lang.reflect.Type

class DeferredResultDecoder(private val objectMapper: ObjectMapper) : JacksonDecoder(objectMapper) {
    override fun decode(response: Response, type: Type): Any {
        if (response.status() == HttpStatus.SC_ACCEPTED) {
            val retryResponse = response.body()
                .asInputStream()
                .use { inputStream -> objectMapper.readValue(inputStream, RetryResponse::class.java) }
            val request = response.request()
            if (!request.headers().containsKey(Constants.DEFERRED_RESULT_HEADER)) {
                request.requestTemplate().header(
                    Constants.DEFERRED_RESULT_HEADER,
                    retryResponse.requestId
                )
            }
            throw RetryableException(
                HttpStatus.SC_ACCEPTED,
                "Waiting response generation",
                response.request().httpMethod(),
                retryResponse.retryAfter,
                response.request()
            )
        }
        return super.decode(response, type)
    }
}
