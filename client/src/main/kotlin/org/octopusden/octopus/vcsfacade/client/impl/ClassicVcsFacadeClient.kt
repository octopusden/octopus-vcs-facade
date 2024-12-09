package org.octopusden.octopus.vcsfacade.client.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import feign.Feign
import feign.Logger
import feign.Request
import feign.httpclient.ApacheHttpClient
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import java.util.Date
import java.util.concurrent.TimeUnit
import org.octopusden.octopus.vcsfacade.client.VcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.VcsFacadeErrorDecoder
import org.octopusden.octopus.vcsfacade.client.VcsFacadeResponseInterceptor
import org.octopusden.octopus.vcsfacade.client.VcsFacadeRetryer
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest

class ClassicVcsFacadeClient(
    apiParametersProvider: VcsFacadeClientParametersProvider, private val mapper: ObjectMapper
) : VcsFacadeClient {
    private var client = createClient(
        apiParametersProvider.getApiUrl(), mapper, apiParametersProvider.getTimeRetryInMillis()
    )

    constructor(apiParametersProvider: VcsFacadeClientParametersProvider) : this(
        apiParametersProvider, getMapper()
    )

    override fun getCommits(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String) =
        client.getCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getCommitsWithFiles(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        commitFilesLimit: Int?
    ) = client.getCommitsWithFiles(sshUrl, fromHashOrRef, fromDate, toHashOrRef, commitFilesLimit)

    override fun getCommit(sshUrl: String, hashOrRef: String) = client.getCommit(sshUrl, hashOrRef)

    override fun getCommitWithFiles(sshUrl: String, hashOrRef: String, commitFilesLimit: Int?) =
        client.getCommitWithFiles(sshUrl, hashOrRef, commitFilesLimit)

    override fun getIssuesFromCommits(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String) =
        client.getIssuesFromCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getTags(sshUrl: String) = client.getTags(sshUrl)

    override fun createTag(sshUrl: String, createTag: CreateTag) = client.createTag(sshUrl, createTag)

    override fun getTag(sshUrl: String, name: String) = client.getTag(sshUrl, name)

    override fun deleteTag(sshUrl: String, name: String) = client.deleteTag(sshUrl, name)

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest) =
        client.searchIssuesInRanges(searchRequest)

    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest) =
        client.createPullRequest(sshUrl, createPullRequest)

    override fun findByIssueKeys(issueKeys: Set<String>) = client.findByIssueKeys(issueKeys)

    override fun findBranchesByIssueKeys(issueKeys: Set<String>) = client.findBranchesByIssueKeys(issueKeys)

    override fun findCommitsByIssueKeys(issueKeys: Set<String>) = client.findCommitsByIssueKeys(issueKeys)

    override fun findCommitsWithFilesByIssueKeys(issueKeys: Set<String>, commitFilesLimit: Int?) =
        client.findCommitsWithFilesByIssueKeys(issueKeys, commitFilesLimit)

    override fun findPullRequestsByIssueKeys(issueKeys: Set<String>) = client.findPullRequestsByIssueKeys(issueKeys)

    override fun reindexRepository(sshUrl: String) = client.reindexRepository(sshUrl)

    @Suppress("unused")
    fun setUrl(apiUrl: String, timeRetryInMillis: Int) {
        client = createClient(apiUrl, mapper, timeRetryInMillis)
    }

    companion object {
        private fun getMapper(): ObjectMapper {
            val objectMapper = jacksonObjectMapper()
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            return objectMapper
        }

        private fun createClient(apiUrl: String, objectMapper: ObjectMapper, timeRetryInMillis: Int): VcsFacadeClient {
            return Feign.builder().client(ApacheHttpClient())
                .options(Request.Options(30, TimeUnit.SECONDS, 30, TimeUnit.SECONDS, true))
                .logger(Slf4jLogger(VcsFacadeClient::class.java)).logLevel(Logger.Level.FULL)
                .encoder(JacksonEncoder(objectMapper))
                .responseInterceptor(VcsFacadeResponseInterceptor(objectMapper))
                .retryer(VcsFacadeRetryer(timeRetryInMillis))
                .decoder(JacksonDecoder(objectMapper)).errorDecoder(VcsFacadeErrorDecoder(objectMapper))
                .target(VcsFacadeClient::class.java, apiUrl)
        }
    }
}
