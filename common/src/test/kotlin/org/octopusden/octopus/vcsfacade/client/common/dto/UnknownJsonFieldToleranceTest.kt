package org.octopusden.octopus.vcsfacade.client.common.dto

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * A newer server may add fields to any of these DTOs, while consumers keep running an older client jar.
 * Every DTO is therefore annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`, so an unknown field
 * is skipped instead of failing the whole response.
 *
 * The mapper below deliberately leaves FAIL_ON_UNKNOWN_PROPERTIES enabled: the client's own default mapper
 * disables it, but `ClassicVcsFacadeClient` also accepts a caller-supplied mapper, and a caller supplying a
 * strict one is exactly how an added field once broke consumers in production. The annotation has to hold
 * regardless of which mapper a consumer passes in.
 */
class UnknownJsonFieldToleranceTest {
    private val strictMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

    @ParameterizedTest(name = "{0}")
    @MethodSource("dtoSamples")
    fun unknownFieldIsIgnored(
        type: Class<*>,
        json: String,
    ) {
        val withUnknownField = json.replaceFirst("{", """{"fieldAddedByANewerServer":"whatever",""")
        assertEquals(
            strictMapper.readValue(json, type),
            strictMapper.readValue(withUnknownField, type),
        )
    }

    companion object {
        private const val REPOSITORY = """{"sshUrl":"ssh://git@host/g/r.git","link":"http://host/g/r","avatar":null}"""
        private const val USER = """{"name":"user","avatar":null}"""
        private const val COMMIT =
            """{"hash":"abc","message":"m","date":0,"author":$USER,"parents":[],"link":"http://l","repository":$REPOSITORY}"""
        private const val FILE_CHANGE = """{"type":"ADD","path":"p","link":"http://l"}"""
        private const val PULL_REQUEST =
            """{"index":1,"title":"t","description":"d","author":$USER,"source":"s","target":"t","assignees":[],""" +
                """"reviewers":[],"status":"OPEN","createdAt":0,"updatedAt":0,"link":"http://l","repository":$REPOSITORY}"""

        private const val BRANCHES_SUMMARY = """{"size":1,"updated":0}"""
        private const val COMMITS_SUMMARY = """{"size":1,"latest":0}"""
        private const val PULL_REQUESTS_SUMMARY = """{"size":1,"updated":0,"status":"OPEN"}"""
        private const val SEARCH_SUMMARY =
            """{"branches":$BRANCHES_SUMMARY,"commits":$COMMITS_SUMMARY,"pullRequests":$PULL_REQUESTS_SUMMARY}"""
        private const val INDEX_REPORT_REPOSITORY = """{"sshUrl":"ssh://git@host/g/r.git","scanRequired":false,"lastScanAt":0}"""

        @JvmStatic
        fun dtoSamples() =
            Stream.of(
                Arguments.of(Repository::class.java, REPOSITORY),
                Arguments.of(User::class.java, USER),
                Arguments.of(Commit::class.java, COMMIT),
                Arguments.of(CommitWithFiles::class.java, """{"commit":$COMMIT,"totalFiles":0,"files":[]}"""),
                Arguments.of(FileChange::class.java, FILE_CHANGE),
                Arguments.of(
                    Branch::class.java,
                    """{"type":"BRANCH","name":"master","hash":"abc","link":"http://l","repository":$REPOSITORY}""",
                ),
                Arguments.of(
                    Tag::class.java,
                    """{"type":"TAG","name":"v1.0","hash":"abc","link":"http://l","repository":$REPOSITORY}""",
                ),
                Arguments.of(
                    Ref::class.java,
                    """{"type":"TAG","name":"v1.0","hash":"abc","link":"http://l","repository":$REPOSITORY}""",
                ),
                Arguments.of(PullRequest::class.java, PULL_REQUEST),
                Arguments.of(PullRequestReviewer::class.java, """{"user":$USER,"approved":true}"""),
                Arguments.of(SearchSummary::class.java, SEARCH_SUMMARY),
                Arguments.of(SearchSummary.SearchBranchesSummary::class.java, BRANCHES_SUMMARY),
                Arguments.of(SearchSummary.SearchCommitsSummary::class.java, COMMITS_SUMMARY),
                Arguments.of(SearchSummary.SearchPullRequestsSummary::class.java, PULL_REQUESTS_SUMMARY),
                Arguments.of(IndexReport::class.java, """{"repositories":[]}"""),
                Arguments.of(IndexReport.IndexReportRepository::class.java, INDEX_REPORT_REPOSITORY),
                Arguments.of(RepositoryRange::class.java, """{"sshUrl":"ssh://git@host/g/r.git","toHashOrRef":"master"}"""),
                Arguments.of(SearchIssueInRangesResponse::class.java, """{"issueRanges":{}}"""),
                Arguments.of(SearchIssuesInRangesRequest::class.java, """{"issueKeys":["I-1"],"ranges":[]}"""),
                Arguments.of(CreatePullRequest::class.java, """{"sourceBranch":"s","targetBranch":"t","title":"ti","description":"d"}"""),
                Arguments.of(CreateTag::class.java, """{"name":"v1.0","hashOrRef":"master","message":"m"}"""),
                Arguments.of(ErrorResponse::class.java, """{"errorCode":"OTHER","errorMessage":"m"}"""),
                Arguments.of(RetryResponse::class.java, """{"retryAfter":0,"requestId":"r"}"""),
            )
    }
}
