package org.octopusden.octopus.vcsfacade.client

import com.fasterxml.jackson.databind.ObjectMapper
import feign.InvocationContext
import feign.ResponseInterceptor
import feign.RetryableException
import org.apache.http.HttpStatus
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.RetryResponse

class VcsFacadeResponseInterceptor(private val objectMapper: ObjectMapper) : ResponseInterceptor {
    override fun intercept(invocationContext: InvocationContext?, chain: ResponseInterceptor.Chain) =
        invocationContext?.let {
            with(it.response()) {
                if (status() == HttpStatus.SC_ACCEPTED) {
                    val retryResponse = body().asInputStream().use { inputStream ->
                        objectMapper.readValue(inputStream, RetryResponse::class.java)
                    }
                    if (!request().headers().containsKey(Constants.DEFERRED_RESULT_HEADER)) {
                        request().requestTemplate().header(Constants.DEFERRED_RESULT_HEADER, retryResponse.requestId)
                    }
                    throw RetryableException(
                        HttpStatus.SC_ACCEPTED,
                        "Waiting for deferred result",
                        request().httpMethod(),
                        retryResponse.retryAfter.time,
                        request()
                    )
                }
            }
            chain.next(it)
        }
}