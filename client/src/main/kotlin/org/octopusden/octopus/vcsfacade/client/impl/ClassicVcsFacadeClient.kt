package org.octopusden.octopus.vcsfacade.client.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import feign.Feign
import feign.Logger
import feign.Request
import feign.httpclient.ApacheHttpClient
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import java.util.Date
import java.util.concurrent.TimeUnit
import org.octopusden.octopus.vcsfacade.client.DeferredResultDecoder
import org.octopusden.octopus.vcsfacade.client.VcsFacadeClient
import org.octopusden.octopus.vcsfacade.client.VcsFacadeErrorDecoder
import org.octopusden.octopus.vcsfacade.client.VcsFacadeRetry
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
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

    override fun getCommitsWithFiles(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String) =
        client.getCommitsWithFiles(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getCommit(sshUrl: String, hashOrRef: String) = client.getCommit(sshUrl, hashOrRef)

    override fun getCommitWithFiles(sshUrl: String, hashOrRef: String) = client.getCommitWithFiles(sshUrl, hashOrRef)

    override fun getIssuesFromCommits(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String) =
        client.getIssuesFromCommits(sshUrl, fromHashOrRef, fromDate, toHashOrRef)

    override fun getTags(sshUrl: String) = client.getTags(sshUrl)

    override fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest) =
        client.searchIssuesInRanges(searchRequest)

    override fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest) =
        client.createPullRequest(sshUrl, createPullRequest)

    override fun findByIssueKey(issueKey: String) = client.findByIssueKey(issueKey)

    override fun findBranchesByIssueKey(issueKey: String) = client.findBranchesByIssueKey(issueKey)

    override fun findCommitsByIssueKey(issueKey: String) = client.findCommitsByIssueKey(issueKey)

    override fun findCommitsWithFilesByIssueKey(issueKey: String) = client.findCommitsWithFilesByIssueKey(issueKey)

    override fun findPullRequestsByIssueKey(issueKey: String) = client.findPullRequestsByIssueKey(issueKey)

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
                .encoder(JacksonEncoder(objectMapper)).decoder(DeferredResultDecoder(objectMapper))
                .errorDecoder(VcsFacadeErrorDecoder(objectMapper)).retryer(VcsFacadeRetry(timeRetryInMillis))
                .logger(Slf4jLogger(VcsFacadeClient::class.java)).logLevel(Logger.Level.FULL)
                .target(VcsFacadeClient::class.java, apiUrl)
        }
    }
}
