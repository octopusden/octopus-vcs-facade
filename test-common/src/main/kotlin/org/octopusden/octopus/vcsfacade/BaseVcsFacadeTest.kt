package org.octopusden.octopus.vcsfacade

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.util.Date
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
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

typealias CheckError = (Pair<Int, String>) -> Unit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTest(
    private val testClient: TestClient,
    private val sshUrlFormat: String
) {
    protected abstract val exceptionsMessageInfo: Map<String, String>
    private val repositoryChangeSets: HashMap<String, Map<String, ChangeSet>> = HashMap()

    @BeforeAll
    fun beforeAllVcsFacadeTests() {
        testClient.importRepository(
            sshUrlFormat.format(PROJECT, REPOSITORY),
            File.createTempFile("BaseVcsFacadeTest_", "").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("dump.zip")!!.copyTo(it)
                }
            }
        )
        repositoryChangeSets[REPOSITORY] =
            testClient.getCommits(sshUrlFormat.format(PROJECT, REPOSITORY)).associateBy { it.message }
        testClient.importRepository(
            sshUrlFormat.format(PROJECT, REPOSITORY_2),
            File.createTempFile("BaseVcsFacadeTest_", "").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("dump2.zip")!!.copyTo(it)
                }
            }
        )
        repositoryChangeSets[REPOSITORY_2] =
            testClient.getCommits(sshUrlFormat.format(PROJECT, REPOSITORY_2)).associateBy { it.message }
    }

    @AfterAll
    fun afterAllVcsFacadeTests() {
        testClient.clearData()
    }

    @ParameterizedTest
    @MethodSource("commits")
    fun getCommitsTest(
        sshUrl: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        expectedMessages: List<String>
    ) {
        requestCommitsInterval(
            sshUrl,
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
    fun getCommitsExceptionTest(
        sshUrl: String,
        fromId: String?,
        fromDate: Date?,
        toId: String,
        exceptionInfo: String,
        status: Int
    ) {
        requestCommitsInterval(
            sshUrl,
            fromId,
            fromDate,
            toId,
            status,
            { Assertions.fail("Response status expected:<$status> but was:<200>") }
        ) { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
    }

    @ParameterizedTest
    @MethodSource("commitById")
    fun getCommitByIdTest(sshUrl: String, commitId: String, commitIdName: Pair<String, String>) {
        requestCommitById(
            sshUrl,
            commitId,
            200,
            { Assertions.assertEquals(commitIdName, it.id to it.message) },
            checkError
        )
    }

    @MethodSource("commitByIdException")
    @ParameterizedTest
    fun getCommitByIdExceptionTest(sshUrl: String, commitId: String, exceptionInfo: String, status: Int) {
        requestCommitById(
            sshUrl,
            commitId,
            status,
            { Assertions.fail("Response status expected:<$status> but was:<200>") },
            { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
        )
    }

    @ParameterizedTest
    @MethodSource("tags")
    fun getTags(sshUrl: String, expectedTags: List<TestTag>) {
        requestTags(
            sshUrl,
            200,
            { Assertions.assertIterableEquals(expectedTags, it.map { tag -> tag.toTestTag() }) },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("tagsException")
    fun getTagsException(sshUrl: String, exceptionInfo: String, status: Int) {
        requestTags(
            sshUrl,
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
    fun createPullRequestTest() { //TODO: assert values
        createPullRequest(
            sshUrlFormat.format(PROJECT, REPOSITORY),
            CreatePullRequest(FEATURE_BRANCH, MAIN_BRANCH, "Test PR title", "Test PR description"),
            200,
            { Assertions.assertTrue { it.index > 0 } },
            checkError
        )
    }

    @ParameterizedTest
    @MethodSource("pullRequestsException")
    fun createPullRequestExceptionTest(
        sshUrl: String,
        sourceBranch: String,
        targetBranch: String,
        exceptionInfo: String,
        status: Int
    ) {
        createPullRequest(
            sshUrl,
            CreatePullRequest(sourceBranch, targetBranch, "Test PR title", "Test PR description"),
            400,
            { Assertions.fail("Response status expected:<$status> but was:<200>") },
            { Assertions.assertEquals(Pair(status, getExceptionMessage(exceptionInfo)), it) }
        )
    }

    @Test
    fun getCommitsFromIdTest() {
        requestCommitsInterval(
            sshUrlFormat.format(PROJECT, REPOSITORY_2),
            "master-25\n".commitId(REPOSITORY_2),
            null,
            "master-36\n".commitId(REPOSITORY_2),
            200,
            { commits ->
                Assertions.assertIterableEquals(
                    getTestCommits("commitsFromId.json"),
                    commits.map { it.toTestCommit() }
                )
            },
            checkError
        )
    }

    @Test
    fun getCommitsFromDateTest() {
        requestCommitsInterval(
            sshUrlFormat.format(PROJECT, REPOSITORY_2),
            null,
            "master-25\n".commitDate(REPOSITORY_2),
            "master-36\n".commitId(REPOSITORY_2),
            200,
            { commits ->
                Assertions.assertIterableEquals(
                    getTestCommits("commitsFromDate.json"),
                    commits.map { it.toTestCommit() }
                )
            },
            checkError
        )
    }

    data class TestTag(val name: String, val commitId: String)

    private fun Tag.toTestTag() = TestTag(name, commitId)

    data class TestCommit(val id: String, val message: String, val parents: Set<String>)

    /*
    BitBucket does trim commit message, but GitLab/Gitea does not!
    TODO: Should such behaviour (imitated by removeSuffix("\n")) be implemented in GitLab/Gitea client?
    */
    private fun Commit.toTestCommit() = TestCommit(id, message.removeSuffix("\n"), parents.toSet())

    private fun getTestCommits(resource: String) =
        OBJECT_MAPPER.readValue(
            BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream(resource),
            object : TypeReference<List<TestCommit>>() {}
        )

    private fun getExceptionMessage(name: String): String {
        return exceptionsMessageInfo.getOrDefault(name, "Not exceptionsMessageInfo by name '$name'")
    }

    private val checkError: CheckError =
        { Assertions.fail<String>("Response status expected:<200> but was: <${it.first}> '${it.second}'") }

    protected abstract fun requestTags(
        sshUrl: String,
        status: Int,
        checkSuccess: (List<Tag>) -> Unit,
        checkError: CheckError
    )

    protected abstract fun requestCommitsInterval(
        sshUrl: String,
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
        sshUrl: String,
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
        sshUrl: String,
        createPullRequest: CreatePullRequest,
        status: Int,
        checkSuccess: (PullRequest) -> Unit,
        checkError: CheckError
    )

    private fun String.changeSet(repository: String): ChangeSet {
        val changeSets = repositoryChangeSets[repository] ?: throw IllegalStateException("No repository '$repository'")
        return changeSets[this]
            ?: throw IllegalStateException("No ChangeSet with message '$this' in repository '$repository'")
    }

    private fun String.commitDate(repository: String): Date = changeSet(repository).authorDate

    protected fun String.commitId(repository: String): String = changeSet(repository).id

    //<editor-fold defaultstate="collapsed" desc="test data">
    private fun commits(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                MESSAGE_3.commitId(REPOSITORY),
                listOf(MESSAGE_3, MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                null,
                MESSAGE_2.commitDate(REPOSITORY),
                MESSAGE_3.commitId(REPOSITORY),
                listOf(MESSAGE_3)
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(REPOSITORY),
                null,
                MESSAGE_3.commitId(REPOSITORY),
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(REPOSITORY),
                null,
                MAIN_BRANCH,
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(REPOSITORY),
                null,
                "refs/heads/master",
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                MESSAGE_2.commitId(REPOSITORY),
                listOf(MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_3.commitId(REPOSITORY),
                null,
                MESSAGE_3.commitId(REPOSITORY),
                emptyList<String>()
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
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
                sshUrlFormat.format(PROJECT, "absent"),
                null,
                null,
                DEFAULT_ID,
                "absent-repo",
                400
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                null,
                null,
                DEFAULT_ID,
                "commitsException_1",
                400
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                DEFAULT_ID,
                null,
                MESSAGE_2.commitId(REPOSITORY),
                "commitById",
                400
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(REPOSITORY),
                null,
                DEFAULT_ID,
                "commitById",
                400
            ),

            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_1.commitId(REPOSITORY),
                MESSAGE_2.commitDate(REPOSITORY),
                MESSAGE_3.commitId(REPOSITORY),
                "commitsException_2",
                400
            )
        )
    }

    private fun commitById(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                MESSAGE_3.commitId(REPOSITORY),
                MESSAGE_3.commitId(REPOSITORY) to MESSAGE_3
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                FEATURE_BRANCH,
                FEATURE_MESSAGE_1.commitId(REPOSITORY) to FEATURE_MESSAGE_1
            )
        )
    }

    private fun commitByIdException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                sshUrlFormat.format(PROJECT, "absent"),
                MESSAGE_3.commitId(REPOSITORY),
                "absent-repo",
                400
            ),
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                DEFAULT_ID,
                "commitById",
                400
            )
        )
    }

    private fun tags(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                sshUrlFormat.format(PROJECT, REPOSITORY),
                listOf(
                    TestTag(
                        TAG_1,
                        MESSAGE_1.commitId(REPOSITORY)
                    ),
                    TestTag(
                        TAG_2,
                        MESSAGE_2.commitId(REPOSITORY)
                    ),
                    TestTag(
                        TAG_3,
                        MESSAGE_3.commitId(REPOSITORY)
                    )
                )
            )
        )
    }

    private fun tagsException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                sshUrlFormat.format(PROJECT, "absent"),
                "absent-repo",
                400
            )
        )
    }

    protected open fun issueCommits(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("ABSENT-1", emptyList<String>()),
            Arguments.of("TEST-1", listOf(MESSAGE_2.commitId(REPOSITORY), MESSAGE_1.commitId(REPOSITORY)))
        )
    }

    private fun issuesInRanges(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MESSAGE_3.commitId(REPOSITORY)
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                MESSAGE_3.commitId(REPOSITORY)
                            )
                        ),
                        "TEST-2" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                MESSAGE_3.commitId(REPOSITORY)
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                "refs/heads/$MAIN_BRANCH"
                            )
                        ),
                        "TEST-2" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                "refs/heads/$MAIN_BRANCH"
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        MESSAGE_INIT.commitId(REPOSITORY),
                        null,
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                MESSAGE_INIT.commitId(REPOSITORY),
                                null,
                                "refs/heads/$MAIN_BRANCH"
                            )
                        ),
                        "TEST-2" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                MESSAGE_INIT.commitId(REPOSITORY),
                                null,
                                "refs/heads/$MAIN_BRANCH"
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        null,
                        MESSAGE_2.commitDate(REPOSITORY),
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-2" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                null,
                                MESSAGE_2.commitDate(REPOSITORY),
                                "refs/heads/$MAIN_BRANCH"
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                setOf("TEST-1"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MESSAGE_3.commitId(REPOSITORY)
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(
                            RepositoryRange(
                                sshUrlFormat.format(PROJECT, REPOSITORY),
                                null,
                                null,
                                MESSAGE_3.commitId(REPOSITORY)
                            )
                        ),
                    )
                )
            ),
            Arguments.of(
                setOf("ABSENT-1"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        null,
                        null,
                        MESSAGE_3.commitId(REPOSITORY)
                    )
                ),
                SearchIssueInRangesResponse(emptyMap())
            ),
            Arguments.of(
                setOf("ABSENT-1"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
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
                        sshUrlFormat.format(PROJECT, REPOSITORY),
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
                        sshUrlFormat.format(PROJECT, REPOSITORY),
                        MESSAGE_3.commitId(REPOSITORY),
                        null,
                        MESSAGE_1.commitId(REPOSITORY)
                    )
                ),
                "commitsException_3",
                400
            ),
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        sshUrlFormat.format(PROJECT, REPOSITORY),
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
            sshUrlFormat.format("absent", REPOSITORY),
            FEATURE_BRANCH,
            MAIN_BRANCH,
            "pr_1",
            400
        ),
        Arguments.of(
            sshUrlFormat.format(PROJECT, "absent"),
            FEATURE_BRANCH,
            MAIN_BRANCH,
            "absent-repo",
            400
        ),
        Arguments.of(
            sshUrlFormat.format(PROJECT, REPOSITORY),
            "absent",
            MAIN_BRANCH,
            "pr_2",
            400
        ),
        Arguments.of(
            sshUrlFormat.format(PROJECT, REPOSITORY),
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
        const val REPOSITORY_2 = "test-repository-2"

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

        val OBJECT_MAPPER = ObjectMapper().registerKotlinModule()
    }
}

