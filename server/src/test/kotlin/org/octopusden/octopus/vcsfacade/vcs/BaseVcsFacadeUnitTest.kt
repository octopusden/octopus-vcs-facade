package org.octopusden.octopus.vcsfacade.vcs

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.BaseVcsFacadeTest
import org.octopusden.octopus.vcsfacade.CheckError
import org.octopusden.octopus.vcsfacade.VcsFacadeApplication
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

@AutoConfigureMockMvc
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = [VcsFacadeApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseVcsFacadeUnitTest(testClient: TestClient, sshUrlFormat: String) :
    BaseVcsFacadeTest(testClient, sshUrlFormat) {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @BeforeAll
    fun beforeAllRepositoryControllerTests() {
        mapper.setLocale(Locale.ENGLISH)
    }

    override fun requestCommitsInterval(
        sshUrl: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/rest/api/2/repository/commits")
                .param("sshUrl", sshUrl)
                .param("fromHashOrRef", fromId)
                .param("fromDate", fromDate?.toVcsFacadeFormat())
                .param("toHashOrRef", toId)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response
        checkResponse(response, status, object : TypeReference<List<Commit>>() {}, checkSuccess, checkError)
    }

    override fun requestCommitById(
        sshUrl: String,
        commitId: String,
        status: Int,
        checkSuccess: (Commit) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/rest/api/2/repository/commit")
                .param("sshUrl", sshUrl)
                .param("hashOrRef", commitId)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response
        checkResponse(response, status, object : TypeReference<Commit>() {}, checkSuccess, checkError)
    }

    override fun requestTags(
        sshUrl: String,
        status: Int,
        checkSuccess: (List<Tag>) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/rest/api/2/repository/tags")
                .param("sshUrl", sshUrl)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response
        checkResponse(response, status, object : TypeReference<List<Tag>>() {}, checkSuccess, checkError)
    }

    override fun requestCommitsByIssueKey(
        issueKey: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/rest/api/2/repository/find/$issueKey/commits")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response
        checkResponse(response, status, object : TypeReference<List<Commit>>() {}, checkSuccess, checkError)
    }

    override fun searchIssuesInRanges(
        searchRequest: SearchIssuesInRangesRequest,
        status: Int,
        checkSuccess: (SearchIssueInRangesResponse) -> Unit,
        checkError: CheckError
    ) {
        val content = mapper.writeValueAsString(searchRequest)
        val response = mvc.perform(
            MockMvcRequestBuilders.post("/rest/api/2/repository/search-issues-in-ranges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response
        checkResponse(
            response,
            status,
            object : TypeReference<SearchIssueInRangesResponse>() {},
            checkSuccess,
            checkError
        )
    }

    override fun createPullRequest(
        sshUrl: String,
        createPullRequest: CreatePullRequest,
        status: Int,
        checkSuccess: (PullRequest) -> Unit,
        checkError: CheckError
    ) {
        val content = mapper.writeValueAsString(createPullRequest)
        val response = mvc.perform(
            MockMvcRequestBuilders.post("/rest/api/2/repository/pull-requests", sshUrl)
                .param("sshUrl", sshUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response
        checkResponse(
            response,
            status,
            object : TypeReference<PullRequest>() {},
            checkSuccess,
            checkError
        )
    }

    private fun <T> checkResponse(
        response: MockHttpServletResponse,
        status: Int,
        typeReference: TypeReference<T>,
        checkSuccess: (T) -> Unit,
        checkError: CheckError
    ) {
        if (HttpStatus.OK == HttpStatus.valueOf(status)) {
            checkSuccess(response.toObject(typeReference))
        } else {
            val err = response.toObject(object : TypeReference<ErrorResponse>() {})
            checkError(Pair(response.status, err.errorMessage))
        }
    }

    private fun <T> MockHttpServletResponse.toObject(typeReference: TypeReference<T>): T =
        mapper.readValue(contentAsByteArray, typeReference)

    private fun Date.toVcsFacadeFormat(): String {
        return FORMATTER.format(toInstant())
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern(ISO_PATTERN)
            .withZone(ZoneId.systemDefault())
    }
}
