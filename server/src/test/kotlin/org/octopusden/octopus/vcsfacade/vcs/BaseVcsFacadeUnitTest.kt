package org.octopusden.octopus.vcsfacade.vcs

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.BaseVcsFacadeTest
import org.octopusden.octopus.vcsfacade.TestService
import org.octopusden.octopus.vcsfacade.VcsFacadeApplication
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@AutoConfigureMockMvc
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = [VcsFacadeApplication::class], webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
abstract class BaseVcsFacadeUnitTest(
    testService: TestService, testClient: TestClient
) : BaseVcsFacadeTest(testService, testClient) {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeAll
    fun beforeAllBaseVcsFacadeUnitTest() {
        objectMapper.setLocale(Locale.ENGLISH)
    }

    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest) =
        mvc.perform(
            MockMvcRequestBuilders.post("/rest/api/2/repository/pull-requests")
                .param("sshUrl", sshUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPullRequest))
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<PullRequest>() {})

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commits")
            .param("sshUrl", sshUrl)
            .param("fromHashOrRef", fromHashOrRef)
            .param("fromDate", fromDate?.toVcsFacadeFormat())
            .param("toHashOrRef", toHashOrRef)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<Commit>>() {})

    override fun getCommitsWithFiles(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        commitFilesLimit: Int?
    ) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commits/files")
            .param("sshUrl", sshUrl)
            .param("fromHashOrRef", fromHashOrRef)
            .param("fromDate", fromDate?.toVcsFacadeFormat())
            .param("toHashOrRef", toHashOrRef)
            .param("commitFilesLimit", commitFilesLimit?.toString())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<CommitWithFiles>>() {})

    override fun getCommit(sshUrl: String, hashOrRef: String) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commit")
            .param("sshUrl", sshUrl)
            .param("hashOrRef", hashOrRef)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<Commit>() {})

    override fun getCommitWithFiles(sshUrl: String, hashOrRef: String, commitFilesLimit: Int?) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commit/files")
            .param("sshUrl", sshUrl)
            .param("hashOrRef", hashOrRef)
            .param("commitFilesLimit", commitFilesLimit?.toString())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<CommitWithFiles>() {})

    private fun <T> MockHttpServletResponse.toObject(typeReference: TypeReference<T>): T {
        if (status / 100 != 2) {
            throw try {
                objectMapper.readValue(this.contentAsByteArray, ErrorResponse::class.java).let {
                    it.errorCode.getException(it.errorMessage)
                }
            } catch (e: Exception) {
                RuntimeException(String(this.contentAsByteArray))
            }
        }
        return objectMapper.readValue(this.contentAsByteArray, typeReference)
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault())

        private fun Date.toVcsFacadeFormat(): String {
            return DATE_FORMATTER.format(toInstant())
        }
    }
}
