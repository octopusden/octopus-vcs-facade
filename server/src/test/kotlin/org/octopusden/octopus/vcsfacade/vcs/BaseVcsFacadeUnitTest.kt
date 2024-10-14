package org.octopusden.octopus.vcsfacade.vcs

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.BaseVcsFacadeTest
import org.octopusden.octopus.vcsfacade.TestService
import org.octopusden.octopus.vcsfacade.VcsFacadeApplication
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
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

    override fun getIssuesFromCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/issues")
            .param("sshUrl", sshUrl)
            .param("fromHashOrRef", fromHashOrRef)
            .param("fromDate", fromDate?.toVcsFacadeFormat())
            .param("toHashOrRef", toHashOrRef)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<String>>() {})

    override fun getTags(sshUrl: String) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/tags")
            .param("sshUrl", sshUrl)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<Tag>>() {})

    override fun createTag(sshUrl: String, createTag: CreateTag) = mvc.perform(
        MockMvcRequestBuilders.post("/rest/api/2/repository/tags")
            .param("sshUrl", sshUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createTag))
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<Tag>() {})

    override fun getTag(sshUrl: String, name: String) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/tag")
            .param("sshUrl", sshUrl)
            .param("name", name)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<Tag>() {})

    override fun deleteTag(sshUrl: String, name: String) = mvc.perform(
        MockMvcRequestBuilders.delete("/rest/api/2/repository/tag")
            .param("sshUrl", sshUrl)
            .param("name", name)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.decodeError()

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest) = mvc.perform(
        MockMvcRequestBuilders.post("/rest/api/2/repository/search-issues-in-ranges")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(searchRequest))
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<SearchIssueInRangesResponse>() {})

    override fun findByIssueKeys(issueKeys: List<String>) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/find")
            .param("issueKeys", *issueKeys.toTypedArray())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<SearchSummary>() {})

    override fun findBranchesByIssueKeys(issueKeys: List<String>) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/branches/find")
            .param("issueKeys", *issueKeys.toTypedArray())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<Branch>>() {})

    override fun findCommitsByIssueKeys(issueKeys: List<String>) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commits/find")
            .param("issueKeys", *issueKeys.toTypedArray())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<Commit>>() {})

    override fun findCommitsWithFilesByIssueKeys(issueKeys: List<String>, commitFilesLimit: Int?) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commits/files/find")
            .param("issueKeys", *issueKeys.toTypedArray())
            .param("commitFilesLimit", commitFilesLimit?.toString())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<CommitWithFiles>>() {})

    override fun findPullRequestsByIssueKeys(issueKeys: List<String>) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/pull-requests/find")
            .param("issueKeys", *issueKeys.toTypedArray())
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<PullRequest>>() {})

    private fun MockHttpServletResponse.decodeError() {
        if (status / 100 != 2) {
            throw try {
                objectMapper.readValue(this.contentAsByteArray, ErrorResponse::class.java).let {
                    it.errorCode.getException(it.errorMessage)
                }
            } catch (e: Exception) {
                RuntimeException(String(this.contentAsByteArray))
            }
        }
    }

    private fun <T> MockHttpServletResponse.toObject(typeReference: TypeReference<T>): T {
        decodeError()
        return objectMapper.readValue(this.contentAsByteArray, typeReference)
    }

    companion object {
        val vcsFacadeHost: String = System.getProperty("test.vcs-facade-host")
            ?: throw Exception("System property 'test.vcs-facade-host' must be defined")

        val vcsHost: String = System.getProperty("test.vcs-host")
            ?: throw Exception("System property 'test.vcs-host' must be defined")

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault())

        private fun Date.toVcsFacadeFormat(): String {
            return DATE_FORMATTER.format(toInstant())
        }
    }
}
