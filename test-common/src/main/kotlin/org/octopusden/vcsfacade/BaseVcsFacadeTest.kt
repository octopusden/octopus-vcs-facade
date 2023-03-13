package org.octopusden.vcsfacade

import org.octopusden.infastructure.bitbucket.test.BitbucketTestClient
import org.octopusden.infastructure.bitbucket.test.dto.ChangeSet
import org.octopusden.infastructure.bitbucket.test.dto.NewChangeSet
import org.octopusden.vcsfacade.client.common.dto.Commit
import org.octopusden.vcsfacade.client.common.dto.PullRequestRequest
import org.octopusden.vcsfacade.client.common.dto.PullRequestResponse
import org.octopusden.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.vcsfacade.client.common.dto.Tag
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
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
import org.junit.platform.commons.logging.LoggerFactory

typealias CheckError = (Pair<Int, String>) -> Unit

const val BITBUCKET_USER = "admin"
const val BITBUCKET_PASSWORD = "admin"
const val BITBUCKET_PROJECT = "test-project"
const val BITBUCKET_REPOSITORY = "test-repository"

const val MAIN_BRANCH = "master"
const val FEATURE_BRANCH = "feature"

const val MESSAGE_INIT = "initial commit"
const val MESSAGE_1 = "TEST-1 First commit"
const val MESSAGE_2 = "TEST-1 Second commit"
const val MESSAGE_3 = "TEST-2 Third commit"

const val FEATURE_MESSAGE_1 = "FEATURE-1 First commit"

const val TAG_1 = "commit-1-tag"
const val TAG_2 = "commit-2-tag"
const val TAG_3 = "commit-3-tag"

const val DEFAULT_ID = "fake-jdhfoshadf9823647928364918gfksjdf-fake"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTest {
    abstract val gitlabHost: String
    abstract val bitbucketHost: String

    private val bitbucketTestClient: BitbucketTestClient by lazy {
        BitbucketTestClient(
            "localhost:7990",
            BITBUCKET_USER,
            BITBUCKET_PASSWORD
        )
    }
    private val commitMessagesChangeSets by lazy { mutableMapOf<String, ChangeSet>() }

    private val bitbucketVcsUrl by lazy { "ssh://git@$bitbucketHost/$BITBUCKET_PROJECT/$BITBUCKET_REPOSITORY.git" }
    private val gitlabVcsUrl by lazy { "git@$gitlabHost/gitlab:test-data/vcs-facade-healthcheck.git" }

    @BeforeAll
    fun beforeAllVcsFacadeTests() {
        generateBitbucketData()
    }

    @AfterAll
    fun afterAllVcsFacadeTests() {
        bitbucketTestClient.clearData()
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
            bitbucketVcsUrl,
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

    private val exceptionsMessageInfo by lazy {
        mapOf(
            "absent-bitbucket-repo" to "Repository $BITBUCKET_PROJECT/absent does not exist.",
            "commitById_2" to "Commit '$DEFAULT_ID' does not exist in repository '$BITBUCKET_REPOSITORY'.",
            "commitById_3" to "Repository 'git@$gitlabHost/gitlab:test-data/absent.git' is not found",

            "commitsException_1" to "Commit '$DEFAULT_ID' does not exist in repository '$BITBUCKET_REPOSITORY'.",
            "commitsException_2" to "Can't find commit '$DEFAULT_ID' in '$gitlabVcsUrl'",
            "commitsException_3" to "Params 'fromId' and 'fromDate' can not be used together",
            "commitsException_5" to "Repository 'git@$gitlabHost/gitlab:test-data/absent.git' is not found",

            "commitsException_6" to "Can't find commit '${MESSAGE_3.commitId()}' in graph but it exists in the '$bitbucketVcsUrl'",

            "tags_2" to "Repository 'git@$gitlabHost/gitlab:test-data/absent.git' is not found",

            "pr_1" to "Project absent does not exist.",
            "pr_2" to "Source branch 'absent' not found in '$BITBUCKET_PROJECT:$BITBUCKET_REPOSITORY'",
            "pr_3" to "Target branch 'absent' not found in '$BITBUCKET_PROJECT:$BITBUCKET_REPOSITORY'"
        )
    }


    private fun getExceptionMessage(name: String): String {
        val message = exceptionsMessageInfo.getOrDefault(name, "Not exceptionsMessageInfo by name '$name'")
        return message.replace("gitHost", gitlabHost)
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

    private fun generateBitbucketData() {
        log.debug { "Generate Bitbucket Dataset" }
        val message1Id = MESSAGE_1.commit().id
        TAG_1.tag(message1Id)
        FEATURE_MESSAGE_1.commit(FEATURE_BRANCH, message1Id, 3)
        TAG_2.tag(MESSAGE_2.commit(waitBeforeSec = 3).id)
        TAG_3.tag(MESSAGE_3.commit(waitBeforeSec = 3).id)
    }

    protected fun String.commit(
        branch: String = MAIN_BRANCH,
        parent: String? = null,
        waitBeforeSec: Long = 0
    ): ChangeSet {
        TimeUnit.SECONDS.sleep(waitBeforeSec)
        val changeSet = bitbucketTestClient.commit(NewChangeSet(this, bitbucketVcsUrl, branch), parent)
        commitMessagesChangeSets[this] = changeSet
        return changeSet
    }

    private fun String.tag(id: String) {
        bitbucketTestClient.tag(bitbucketVcsUrl, id, this)
    }

    protected fun String.changeSet(): ChangeSet =
        commitMessagesChangeSets[this] ?: throw IllegalStateException("No ChangeSet for message: '$this'")

    protected fun String.dateBeforeCommit(): Date = Date(changeSet().authorDate.time - 1000)

    protected fun String.commitId(): String = changeSet().id

    //<editor-fold defaultstate="collapsed" desc="test data">
    private fun commits(): Stream<Arguments> {
        return Stream.of(
            //<editor-fold defaultstate="collapsed" desc="gitlab data">
            Arguments.of(
                gitlabVcsUrl,
                null,
                null,
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                listOf(MESSAGE_3, MESSAGE_2, MESSAGE_1)
            ),
            Arguments.of(
                gitlabVcsUrl,
                null,
                "2018-02-22T15:10:40.000+03:00".isoToDate(),
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                gitlabVcsUrl,
                "321d4908aef10bafa1488f9b053270acc29f3d78",
                null,
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                gitlabVcsUrl,
                null,
                null,
                "00cc61dd4c3eca64d12e6beceff1a40a436962f5",
                listOf(MESSAGE_2, MESSAGE_1)
            ),
            Arguments.of(
                gitlabVcsUrl,
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                null,
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                listOf(MESSAGE_3)
            ),
            //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="bitbucket data">
            Arguments.of(
                bitbucketVcsUrl,
                null,
                null,
                MESSAGE_3.commitId(),
                listOf(MESSAGE_3, MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            ),
            Arguments.of(
                bitbucketVcsUrl,
                null,
                MESSAGE_2.dateBeforeCommit(),
                MESSAGE_3.commitId(),
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                bitbucketVcsUrl,
                MESSAGE_1.commitId(),
                null,
                MESSAGE_3.commitId(),
                listOf(MESSAGE_3, MESSAGE_2)
            ),
            Arguments.of(
                bitbucketVcsUrl,
                null,
                null,
                MESSAGE_2.commitId(),
                listOf(MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            ),
            Arguments.of(
                bitbucketVcsUrl,
                MESSAGE_3.commitId(),
                null,
                MESSAGE_3.commitId(),
                emptyList<String>()
            ),
            Arguments.of(
                bitbucketVcsUrl,
                null,
                null,
                MAIN_BRANCH,
                listOf(MESSAGE_3, MESSAGE_2, MESSAGE_1, MESSAGE_INIT)
            )
            //</editor-fold>
        )
    }

    private fun commitsException(): Stream<Arguments> {
        return Stream.of(
            //<editor-fold defaultstate="collapsed" desc="gitlab data">
            Arguments.of(
                "git@$gitlabHost/gitlab:test-data/absent.git",
                null,
                null,
                DEFAULT_ID,
                "commitsException_5",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                null,
                null,
                DEFAULT_ID,
                "commitsException_2",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                DEFAULT_ID,
                null,
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                "commitsException_2",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                "321d4908aef10bafa1488f9b053270acc29f3d78",
                null,
                DEFAULT_ID,
                "commitsException_2",
                400
            ),
            Arguments.of(
                "git@$gitlabHost/gitlab:test-data/absent.git",
                null,
                null,
                DEFAULT_ID,
                "commitsException_5",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                null,
                null,
                DEFAULT_ID,
                "commitsException_2",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                DEFAULT_ID,
                null,
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                "commitsException_2",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                "321d4908aef10bafa1488f9b053270acc29f3d78",
                null,
                DEFAULT_ID,
                "commitsException_2",
                400
            ),
            Arguments.of(
                gitlabVcsUrl,
                "321d4908aef10bafa1488f9b053270acc29f3d78",
                "2018-02-22T15:10:40.000+03:00".isoToDate(),
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                "commitsException_3",
                400
            ),
            //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="bitbucket data">
            Arguments.of(
                "ssh://git@$bitbucketHost/$BITBUCKET_PROJECT/absent.git",
                null,
                null,
                DEFAULT_ID,
                "absent-bitbucket-repo",
                400
            ),
            Arguments.of(
                bitbucketVcsUrl,
                null,
                null,
                DEFAULT_ID,
                "commitsException_1",
                400
            ),
            Arguments.of(
                bitbucketVcsUrl,
                DEFAULT_ID,
                null,
                MESSAGE_2.commitId(),
                "commitsException_1",
                400
            ),
            Arguments.of(
                bitbucketVcsUrl,
                MESSAGE_1.commitId(),
                null,
                DEFAULT_ID,
                "commitsException_1",
                400
            ),

            Arguments.of(
                bitbucketVcsUrl,
                MESSAGE_1.commitId(),
                MESSAGE_2.dateBeforeCommit(),
                MESSAGE_3.commitId(),
                "commitsException_3",
                400
            )
            //</editor-fold>
        )
    }

    private fun commitById(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                bitbucketVcsUrl,
                MESSAGE_3.commitId(),
                MESSAGE_3.commitId() to MESSAGE_3
            )
        )
    }

    private fun commitByIdException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                "ssh://git@$bitbucketHost/$BITBUCKET_PROJECT/absent.git",
                MESSAGE_3.commitId(),
                "absent-bitbucket-repo",
                400
            ),
            Arguments.of(
                bitbucketVcsUrl,
                DEFAULT_ID,
                "commitById_2",
                400
            ),
            Arguments.of(
                "git@$gitlabHost/gitlab:test-data/absent.git",
                "9320183f5d5f5868fdb82b36e3abd6f9d1424114",
                "commitById_3",
                400
            )
        )
    }

    private fun tags(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                bitbucketVcsUrl,
                listOf(
                    Tag(MESSAGE_3.commitId(), TAG_3),
                    Tag(MESSAGE_2.commitId(), TAG_2),
                    Tag(MESSAGE_1.commitId(), TAG_1)
                )
            ),
            Arguments.of(
                gitlabVcsUrl,
                listOf(
                    Tag("9320183f5d5f5868fdb82b36e3abd6f9d1424114", TAG_3),
                    Tag("00cc61dd4c3eca64d12e6beceff1a40a436962f5", TAG_2),
                    Tag("321d4908aef10bafa1488f9b053270acc29f3d78", TAG_1)
                )
            )
        )
    }

    private fun tagsException(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                "ssh://git@$bitbucketHost/$BITBUCKET_PROJECT/absent.git",
                "absent-bitbucket-repo",
                400
            ),
            Arguments.of(
                "git@$gitlabHost/gitlab:test-data/absent.git",
                "tags_2",
                400
            )
        )
    }

    private fun issueCommits(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("ABSENT-1", emptyList<String>()),
            Arguments.of("TEST-1", listOf(MESSAGE_2.commitId(), MESSAGE_1.commitId()))
        )
    }

    private fun issuesInRanges(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                bitbucketVcsUrl,
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
                        null,
                        null,
                        MESSAGE_3.commitId()
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(RepositoryRange(bitbucketVcsUrl, null, null, MESSAGE_3.commitId())),
                        "TEST-2" to setOf(RepositoryRange(bitbucketVcsUrl, null, null, MESSAGE_3.commitId()))
                    )
                )
            ),
            Arguments.of(
                bitbucketVcsUrl,
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
                        null,
                        null,
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(RepositoryRange(bitbucketVcsUrl, null, null, "refs/heads/$MAIN_BRANCH")),
                        "TEST-2" to setOf(RepositoryRange(bitbucketVcsUrl, null, null, "refs/heads/$MAIN_BRANCH"))
                    )
                )
            ),
            Arguments.of(
                bitbucketVcsUrl,
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
                        null,
                        MESSAGE_3.dateBeforeCommit(),
                        "refs/heads/$MAIN_BRANCH"
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(),
                        "TEST-2" to setOf(
                            RepositoryRange(
                                bitbucketVcsUrl,
                                null,
                                MESSAGE_3.dateBeforeCommit(),
                                "refs/heads/$MAIN_BRANCH"
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                bitbucketVcsUrl,
                setOf("TEST-1"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
                        null,
                        null,
                        MESSAGE_3.commitId()
                    )
                ),
                SearchIssueInRangesResponse(
                    mapOf(
                        "TEST-1" to setOf(RepositoryRange(bitbucketVcsUrl, null, null, MESSAGE_3.commitId())),
                    )
                )
            ),
            Arguments.of(
                bitbucketVcsUrl,
                setOf("ABSENT-1"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
                        null,
                        null,
                        MESSAGE_3.commitId()
                    )
                ),
                SearchIssueInRangesResponse(emptyMap())
            ),
            Arguments.of(
                bitbucketVcsUrl,
                setOf("ABSENT-1"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
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
                        bitbucketVcsUrl,
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
                        bitbucketVcsUrl,
                        MESSAGE_3.commitId(),
                        null,
                        MESSAGE_1.commitId()
                    )
                ),
                "commitsException_6",
                400
            ),
            Arguments.of(
                setOf("TEST-1", "TEST-2"),
                setOf(
                    RepositoryRange(
                        bitbucketVcsUrl,
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
            "ssh://git@$bitbucketHost/absent/$BITBUCKET_REPOSITORY.git",
            FEATURE_BRANCH,
            MAIN_BRANCH,
            "pr_1",
            400
        ),
        Arguments.of(
            "ssh://git@$bitbucketHost/$BITBUCKET_PROJECT/absent.git",
            FEATURE_BRANCH,
            MAIN_BRANCH,
            "absent-bitbucket-repo",
            400
        ),
        Arguments.of(
            bitbucketVcsUrl,
            "absent",
            MAIN_BRANCH,
            "pr_2",
            400
        ),
        Arguments.of(
            bitbucketVcsUrl,
            FEATURE_BRANCH,
            "absent",
            "pr_3",
            400
        )
    )
    //</editor-fold>

    companion object {
        protected val log = LoggerFactory.getLogger(this::class.java)
    }
}

