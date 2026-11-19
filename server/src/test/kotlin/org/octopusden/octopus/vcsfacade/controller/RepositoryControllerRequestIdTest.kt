package org.octopusden.octopus.vcsfacade.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.octopusden.octopus.vcsfacade.client.common.Constants
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.service.RequestProcessor
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.lang.reflect.Type

class RepositoryControllerRequestIdTest {
    private val sshUrl = "ssh://git@example.com/repo"
    private val repository = Repository(sshUrl = sshUrl, link = "https://example.com/repo")
    private val vcsManager =
        mock(VcsManager::class.java).apply {
            `when`(getRepository(sshUrl)).thenReturn(repository)
        }
    private val requestProcessor = RecordingRequestProcessor()
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(RepositoryController(vcsManager, requestProcessor))
            .setControllerAdvice(ExceptionInfoHandler())
            .build()

    @Test
    fun `rejects non-UUID request id with 400`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/rest/api/2/repository")
                    .param("sshUrl", sshUrl)
                    .header(Constants.DEFERRED_RESULT_HEADER, "not-a-uuid")
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isBadRequest)

        assertNull(requestProcessor.lastRequestId)
    }

    @Test
    fun `passes valid UUID request id to processor`() {
        val requestId = "123e4567-e89b-12d3-a456-426614174000"

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/rest/api/2/repository")
                    .param("sshUrl", sshUrl)
                    .header(Constants.DEFERRED_RESULT_HEADER, requestId)
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)

        assertEquals(requestId, requestProcessor.lastRequestId)
    }

    private class RecordingRequestProcessor : RequestProcessor {
        var lastRequestId: String? = null

        override fun <T : Any> process(
            requestId: String,
            type: Type,
            func: () -> T,
        ): T {
            lastRequestId = requestId
            return func()
        }
    }
}
