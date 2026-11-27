package org.octopusden.octopus.vcsfacade.config

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidRequestException
import org.octopusden.octopus.vcsfacade.service.ProvisioningTargetResolver
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CreateTargetInterceptorTest {
    private val resolver = mock(ProvisioningTargetResolver::class.java)
    private val interceptor = ProvisioningWebConfig.CreateTargetInterceptor(resolver)

    private fun post(query: String?) = MockHttpServletRequest("POST", "/rest/api/2/repository").apply { queryString = query }

    @Test
    fun `a malformed escape in the query is an invalid request, not a server error`() {
        assertThrows(InvalidRequestException::class.java) { interceptor.preHandle(post("sshUrl=%zz"), MockHttpServletResponse(), Any()) }
        verifyNoInteractions(resolver)
    }

    @Test
    fun `a missing sshUrl is an invalid request`() {
        assertThrows(InvalidRequestException::class.java) { interceptor.preHandle(post("other=1"), MockHttpServletResponse(), Any()) }
        verifyNoInteractions(resolver)
    }
}
