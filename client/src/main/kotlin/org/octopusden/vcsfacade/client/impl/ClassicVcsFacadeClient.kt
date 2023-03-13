package org.octopusden.vcsfacade.client.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.octopusden.vcsfacade.client.DeferredResultDecoder
import org.octopusden.vcsfacade.client.VcsFacadeClient
import org.octopusden.vcsfacade.client.VcsFacadeErrorDecoder
import org.octopusden.vcsfacade.client.VcsFacadeRetry
import org.octopusden.vcsfacade.client.common.dto.Commit
import org.octopusden.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.vcsfacade.client.common.dto.Tag
import feign.Feign
import feign.Logger
import feign.Request
import feign.httpclient.ApacheHttpClient
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import java.util.Date
import java.util.concurrent.TimeUnit

class ClassicVcsFacadeClient(apiParametersProvider: VcsFacadeClientParametersProvider, private val mapper: ObjectMapper) : VcsFacadeClient {
    private var client =
        createClient(apiParametersProvider.getApiUrl(), mapper, apiParametersProvider.getTimeRetryInMillis())

    constructor(apiParametersProvider: VcsFacadeClientParametersProvider) : this(
        apiParametersProvider,
        getMapper()
    )

    override fun getCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<Commit> {
        return client.getCommits(vcsPath, fromId, fromDate, toId)
    }

    override fun getCommits(issueKey: String): List<Commit> {
        return client.getCommits(issueKey)
    }

    override fun getCommit(vcsPath: String, cid: String): Commit {
        return client.getCommit(vcsPath, cid)
    }

    override fun getTags(vcsUrl: String): List<Tag> {
        return client.getTags(vcsUrl)
    }

    override fun getIssuesFromCommits(vcsPath: String, fromId: String?, fromDate: Date?, toId: String): List<String> {
        return client.getIssuesFromCommits(vcsPath, fromId, fromDate, toId)
    }

    override fun analyzeRepositoryGraph(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse {
        return client.analyzeRepositoryGraph(searchRequest)
    }

    override fun createPullRequest(vcsPath: String, pullRequestRequest: PullRequestRequest): PullRequestResponse
        = client.createPullRequest(vcsPath, pullRequestRequest)

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
            return Feign.builder()
                .client(ApacheHttpClient())
                .options(Request.Options(30, TimeUnit.SECONDS, 30, TimeUnit.SECONDS, true))
                .encoder(JacksonEncoder(objectMapper))
                .decoder(DeferredResultDecoder(objectMapper))
                .errorDecoder(VcsFacadeErrorDecoder(objectMapper))
                .retryer(VcsFacadeRetry(timeRetryInMillis))
                .logger(Slf4jLogger(VcsFacadeClient::class.java))
                .logLevel(Logger.Level.FULL)
                .target(VcsFacadeClient::class.java, apiUrl)
        }
    }
}
