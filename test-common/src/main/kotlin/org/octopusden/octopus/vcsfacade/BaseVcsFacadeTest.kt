package org.octopusden.octopus.vcsfacade

import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.infrastructure.common.test.dto.ChangeSet
import org.octopusden.octopus.infrastructure.common.test.dto.NewChangeSet
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

typealias CheckError = (Pair<Int, String>) -> Unit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTest(private val testClient: TestClient, val vcsRootFormat: String) {
    protected abstract val exceptionsMessageInfo: Map<String, String>
    private val commitMessagesChangeSets = mutableMapOf<String, ChangeSet>()

    @BeforeAll
    fun beforeAllVcsFacadeTests() {
        val dump = File.createTempFile("BaseVcsFacadeTest_", "").apply {
            this.outputStream().use {
                BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("dump.zip")!!.copyTo(it)
            }
        }
        val vcsUrl = vcsRootFormat.format(PROJECT, REPOSITORY)
        testClient.importRepository(vcsUrl, dump)
        testClient.getCommits(vcsUrl).forEach {
            commitMessagesChangeSets[it.message] = it
        }
    }

    @AfterAll
    fun afterAllVcsFacadeTests() {
        testClient.clearData()
    }

    @ParameterizedTest
    @MethodSource("commits")
    fun getCommitsTest(
        repository: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        expectedMessages: Collection<String>
    ) {
        requestCommitsInterval(
            repository,
            fromId,
            fromDate,
            toId,
            200,
            { Assertions.assertIterableEquals(expectedMessages, it.map { c -> c.message }) },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("commitsException")
    fun getCommitExceptionTest(
        repository: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        exceptionInfo: String,
        status: Int
    ) {
        requestCommitsInterval(
            repository,
            fromId,
            fromDate,
            toId,
            status,
            { Assertions.fail("Response status expected:<$status> but was:<200>") }
        ) { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
    }

    @ParameterizedTest
    @MethodSource("commitById")
    fun getCommitByIdTest(repository: String, commitId: String, commitIdName: Pair<String, String>) {
        requestCommitById(
            repository,
            commitId,
            200,
            { Assertions.assertEquals(commitIdName, it.id to it.message) },
            checkError
        )
    }

    @MethodSource("commitByIdException")
    @ParameterizedTest
    fun getCommitByIdExceptionTest(repository: String, commitId: String, exceptionInfo: String, status: Int) {
        requestCommitById(
            repository,
            commitId,
            status,
            { Assertions.fail("Response status expected:<$status> but was:<200>") },
            { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
        )
    }

    @ParameterizedTest
    @MethodSource("tags")
    fun getTags(repository: String, expectedTags: List<Tag>) {
        requestTags(
            repository,
            200,
            { Assertions.assertEquals(expectedTags, it) },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("tagsException")
    fun getTagsException(repository: String, exceptionInfo: String, status: Int) {
        requestTags(
            repository,
            status,
            { Assertions.fail("Response status expected:<$status> but was:<200>") },
            { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
        )
    }

    @ParameterizedTest
    @MethodSource("issueCommits")
    fun getCommitsByIssueKeyTest(issueKey: String, expectedCommitIds: List<String>) {
        requestCommitsByIssueKey(
            issueKey,
            200,
            { Assertions.assertEquals(expectedCommitIds, it.map { c -> c.id }) },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("issuesInRanges")
    fun searchIssuesInRangesTest(
        repository: String,
        issues: Set<String>,
        ranges: Set<RepositoryRange>,
        expectedData: SearchIssueInRangesResponse
    ) {
        searchIssuesInRanges(
            SearchIssuesInRangesRequest(issues, ranges),
            200,
            { Assertions.assertEquals(expectedData, it) },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("issuesInRangesException")
    fun searchIssuesInRangesExceptionTest(
        issues: Set<String>,
        ranges: Set<RepositoryRange>,
        exceptionInfo: String,
        status: Int
    ) {
        searchIssuesInRanges(
            SearchIssuesInRangesRequest(issues, ranges),
            status,
            { Assertions.fail("Response status expected:<$status> but was:<200>") },
            { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
        )
    }

    @Test
    fun createPullRequestTest() {
        createPullRequest(
            vcsRootFormat.format(PROJECT, REPOSITORY),
            PullRequestRequest(FEATURE_BRANCH, MAIN_BRANCH, "Test PR title", "Test PR description"),
            200,
            { Assertions.assertTrue { it.id > 0 } },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("pullRequestsException")
    fun createPullRequestExceptionTest(
        repository: String,
        sourceBranch: String,
        targetBranch: String,
        exceptionInfo: String,
        status: Int
    ) {
        createPullRequest(
            repository,
            PullRequestRequest(sourceBranch, targetBranch, "Test PR title", "Test PR description"),
            400,
            { Assertions.fail("Response status expected:<$status> but was:<200>") },
            { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
        )
    }

    private fun getExceptionMessage(name: String): String {
        return exceptionsMessageInfo.getOrDefault(name, "Not exceptionsMessageInfo by name '$name'")
    }

    private val checkError: CheckError =
        { Assertions.fail<String>("Response status expected:<200> but was: <${it.first}> '${it.second}'") }

    protected abstract fun requestTags(
        repository: String,
        status: Int,
        checkSuccess: (List<Tag>) -> Unit,
        checkError: CheckError
    )

    protected abstract fun requestCommitsInterval(
        repository: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError,
    )

    protected abstract fun requestCommitsByIssueKey(
        issueKey: String,
        status: Int,
        checkSuccess: (List<Commit>) -> Unit,
        checkError: CheckError
    )

    protected abstract fun requestCommitById(
        vcsPath: String,
        commitId: String,
        status: Int,
        checkSuccess: (Commit) -> Unit,
        checkError: CheckError
    )

    protected abstract fun searchIssuesInRanges(
        searchRequest: SearchIssuesInRangesRequest,
        status: Int,
        checkSuccess: (SearchIssueInRangesResponse) -> Unit,
        checkError: CheckError
    )

    protected abstract fun createPullRequest(
        repository: String,
        pullRequestRequest: PullRequestRequest,
        status: Int,
        checkSuccess: (PullRequestResponse) -> Unit,
        checkError: CheckError
    )

    private fun String.isoToDate(): Date {
        return OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .toInstant()
            .toEpochMilli()
            .let { Date(it) }
    }

    protected fun String.commit(
        branch: String = MAIN_BRANCH,
        parent: String? = null,
        waitBeforeSec: Long = 0
    ): ChangeSet {
        TimeUnit.SECONDS.sleep(waitBeforeSec)
        val changeSet = testClient.commit(NewChangeSet(this, vcsRootFormat.format(PROJECT, REPOSITORY), branch), parent)
        commitMessagesChangeSets[this] = changeSet
        return changeSet
    }

    private fun String.tag(id: String) {
        testClient.tag(vcsRootFormat.format(PROJECT, REPOSITORY), id, this)
    }

    protected fun String.changeSet(): ChangeSet =
        commitMessagesChangeSets[this] ?: throw IllegalStateException("No ChangeSet for message: '$this'")

    protected fun String.dateBeforeCommit(): Date = Date(changeSet().authorDate.time - 1000)

    protected fun String.commitId(): String = changeSet().id

    //<editor-fold defaultstate="collapsed" desc="test data">
    private fun commits(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                MESSAGE_3.commitId(),
                listOf(MESSAGE_3, MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                null,
                MESSAGE_2.dateBeforeCommit(),
                MESSAGE_3.commitId(),
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(),
                null,
                MESSAGE_3.commitId(),
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                MESSAGE_2.commitId(),
                listOf(MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                MESSAGE_3.commitId(),
                null,
                MESSAGE_3.commitId(),
                emptyList<String>()
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                MAIN_BRANCH,
                listOf(MESSAGE_3, MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            )
        )
    }

    private fun commitsException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, "absent"),
                null,
                null,
                DEFAULT_ID,
                "absent-bitbucket-repo",
                400
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                DEFAULT_ID,
                "commitsException_1",
                400
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                DEFAULT_ID,
                null,
                MESSAGE_2.commitId(),
                "commitsException_1",
                400
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(),
                null,
                DEFAULT_ID,
                "commitsException_1",
                400
            ),

            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(),
                MESSAGE_2.dateBeforeCommit(),
                MESSAGE_3.commitId(),
                "commitsException_2",
                400
            )
        )
    }

    private fun commitById(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                MESSAGE_3.commitId(),
                MESSAGE_3.commitId() to MESSAGE_3
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                FEATURE_BRANCH,
                FEATURE_MESSAGE_1.commitId() to FEATURE_MESSAGE_1
            )
        )
    }

    private fun commitByIdException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, "absent"),
                MESSAGE_3.commitId(),
                "absent-bitbucket-repo",
                400
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                DEFAULT_ID,
                "commitById",
                400
            )
        )
    }

    private fun tags(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                listOf(
                    Tag(MESSAGE_3.commitId(), TAG_3),
                    Tag(MESSAGE_2.commitId(), TAG_2),
                    Tag(MESSAGE_1.commitId(), TAG_1)
                )
            )
        )
    }

    private fun tagsException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, "absent"),
                "absent-bitbucket-repo",
                400
            )
        )
    }

    protected open fun issueCommits(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("ABSENT-1", emptyList<String>()),
            Arguments.of("TEST-1", listOf(MESSAGE_2.commitId(), MESSAGE_1.commitId()))
        )
    }

    private fun issuesInRanges(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MESSAGE_3.commitId()
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                vcsRootFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                MESSAGE_3.commitId()
                            )),
                        "TEST-2" to setOf(
                            RepositoryRange(
                                vcsRootFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                MESSAGE_3.commitId()
                            ))
                    )
                )
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                vcsRootFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                "refs/heads/$MAIN_BRANCH"
                            )),
                        "TEST-2" to setOf(
                            RepositoryRange(
                                vcsRootFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                "refs/heads/$MAIN_BRANCH"
                            ))
                    )
                )
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        MESSAGE_3.dateBeforeCommit(),
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-2" to setOf(
                            RepositoryRange(
                                vcsRootFormat.format(PROJECT, REPOSITORY),
                                null,
                                MESSAGE_3.dateBeforeCommit(),
                                "refs/heads/$MAIN_BRANCH"
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                setOf("TEST-1"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MESSAGE_3.commitId()
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                vcsRootFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                MESSAGE_3.commitId()
                            )),
                    )
                )
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                setOf("ABSENT-1"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MESSAGE_3.commitId()
                    )
                ),
                SearchIssueInRangesResponse(emptyMap())
            ),
            Arguments.of(
                vcsRootFormat.format(PROJECT, REPOSITORY),
                setOf("ABSENT-1"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MAIN_BRANCH
                    )
                ),
                SearchIssueInRangesResponse(emptyMap())
            )
        )
    }

    private fun issuesInRangesException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                setOf("TEST-1"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        DEFAULT_ID
                    )
                ),
                "commitsException_1",
                400
            ),
            Arguments.of(
                setOf("RELENG-1637", "RELENG-1609"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        MESSAGE_3.commitId(),
                        null,
                        MESSAGE_1.commitId()
                    )
                ),
                "commitsException_3",
                400
            ),
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        vcsRootFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        DEFAULT_ID
                    )
                ),
                "commitsException_1",
                400
            ),
        )
    }

    private fun pullRequestsException(): Stream<Arguments> = Stream.of(
        Arguments.of(
            vcsRootFormat.format("absent", REPOSITORY),
            FEATURE_BRANCH,
            MAIN_BRANCH,
            "pr_1",
            400
        ),
        Arguments.of(
            vcsRootFormat.format(PROJECT, "absent"),
            FEATURE_BRANCH,
            MAIN_BRANCH,
            "absent-bitbucket-repo",
            400
        ),
        Arguments.of(
            vcsRootFormat.format(PROJECT, REPOSITORY),
            "absent",
            MAIN_BRANCH,
            "pr_2",
            400
        ),
        Arguments.of(
            vcsRootFormat.format(PROJECT, REPOSITORY),
            FEATURE_BRANCH,
            "absent",
            "pr_3",
            400
        )
    )
    //</editor-fold>

    companion object {
        const val BITBUCKET_USER = "admin"
        const val BITBUCKET_PASSWORD = "admin"

        const val GITLAB_USER = "root"
        const val GITLAB_PASSWORD = "VomkaEa6PD1OIgY7dQVbPUuO8wi9RMCaZw/i9yPXcI0="

        const val GITEA_USER = "test-admin"
        const val GITEA_PASSWORD = "test-admin"

        const val PROJECT = "test-project"
        const val REPOSITORY = "test-repository"

        const val MAIN_BRANCH = "master"
        const val FEATURE_BRANCH = "feature/FEATURE-1"

        const val MESSAGE_INIT = "initial commit"
        const val MESSAGE_1 = "TEST-1 First commit"
        const val MESSAGE_2 = "TEST-1 Second commit"
        const val MESSAGE_3 = "TEST-2 Third commit"

        const val FEATURE_MESSAGE_1 = "FEATURE-1 First commit"

        const val TAG_1 = "commit-1-tag"
        const val TAG_2 = "commit-2-tag"
        const val TAG_3 = "commit-3-tag"

        const val DEFAULT_ID = "0123456789abcde"
    }
}

