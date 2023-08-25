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
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
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
@ActiveProfiles("test")
abstract class BaseRepositoryControllerTest(testClient: TestClient, vcsRootFormat: String) :
    BaseVcsFacadeTest(testClient, vcsRootFormat) {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @BeforeAll
    fun beforeAllRepositoryControllerTests() {
        mapper.setLocale(Locale.ENGLISH)
    }

    override fun requestCommitsInterval(
        repository: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/repository/commits")
                .param("vcsPath", repository)
                .param("to", toId)
                .param("from", fromId)
                .param("fromDate", fromDate?.toVcsFacadeFormat())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response

        checkResponse(response, status, object : TypeReference<List<Commit>>() {}, checkSuccess, checkError)
    }

    override fun requestCommitById(
        vcsPath: String,
        commitId: String,
        status: Int,
        checkSuccess: (Commit) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/repository/commit")
                .param("vcsPath", vcsPath)
                .param("commitId", commitId)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().`is`(status))
            .andReturn()
            .response

        checkResponse(response, status, object : TypeReference<Commit>() {}, checkSuccess, checkError)
    }

    override fun requestTags(
        repository: String,
        status: Int,
        checkSuccess: (List<Tag>) -> Unit,
        checkError: CheckError
    ) {
        val response = mvc.perform(
            MockMvcRequestBuilders.get("/repository/tags")
                .param("vcsPath", repository)
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
            MockMvcRequestBuilders.get("/repository/issues/$issueKey")

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
            MockMvcRequestBuilders.post("/repository/search-issues-in-ranges")
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
        repository: String,
        pullRequestRequest: PullRequestRequest,
        status: Int,
        checkSuccess: (PullRequestResponse) -> Unit,
        checkError: CheckError
    ) {
        val content = mapper.writeValueAsString(pullRequestRequest)

        val response = mvc.perform(
            MockMvcRequestBuilders.post("/repository/pull-requests?vcsUrl={}", repository)
                .param("vcsPath", repository)
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
            object : TypeReference<PullRequestResponse>() {},
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
        mapper.readValue(this.contentAsByteArray, typeReference)

    private fun Date.toVcsFacadeFormat(): String {
        return FORMATTER.format(toInstant())
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern(ISO_PATTERN)
            .withZone(ZoneId.systemDefault())
    }
}
