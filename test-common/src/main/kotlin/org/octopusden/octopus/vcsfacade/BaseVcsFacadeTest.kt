package org.octopusden.octopus.vcsfacade

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
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.RepositoryRange
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTest(
    protected val testService: TestService,
    protected val testClient: TestClient
) {
    @BeforeAll
    fun beforeAllBaseVcsFacadeTest() {
        testClient.importRepository(
            testService.sshUrl(GROUP, REPOSITORY),
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$REPOSITORY").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("$GROUP-$REPOSITORY.zip")!!.copyTo(it)
                }
            }
        )
        testClient.importRepository(
            testService.sshUrl(GROUP, REPOSITORY_2),
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$REPOSITORY_2").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("$GROUP-$REPOSITORY_2.zip")!!
                        .copyTo(it)
                }
            }
        )
        createPullRequest(
            testService.sshUrl(GROUP, REPOSITORY_2),
            CreatePullRequest("feature/ISSUE-4", "master", "[ISSUE-4] test PR", "Test PR description")
        )
        createPullRequest(
            testService.sshUrl(GROUP, REPOSITORY_2),
            CreatePullRequest("ISSUE-6-and-ISSUE-7", "master", "ISSUE-6, ISSUE-7 test PR 2", "Test PR 2 description")
        )
    }

    @AfterAll
    fun afterAllBaseVcsFacadeTest() {
        testClient.clearData()
    }

    @ParameterizedTest
    @MethodSource("createPullRequestFailsArguments")
    fun createPullRequestFailsTest(
        group: String,
        repository: String,
        createPullRequest: CreatePullRequest,
        exceptionClass: Class<out Throwable>
    ) {
        Assertions.assertThrows(exceptionClass) {
            createPullRequest(testService.sshUrl(group, repository), createPullRequest)
        }
    }

    @ParameterizedTest
    @MethodSource("getCommitsArguments")
    fun getCommitsTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        commitsFile: String
    ) = Assertions.assertIterableEquals(
        testService.getCommits(commitsFile),
        getCommits(testService.sshUrl(group, repository), fromHashOrRef, fromDate, toHashOrRef)
    )

    @ParameterizedTest
    @MethodSource("getCommitsFailsArguments")
    fun getCommitsFailsTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            getCommits(testService.sshUrl(group, repository), fromHashOrRef, fromDate, toHashOrRef)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("getCommitsWithFilesArguments")
    fun getCommitsWithFilesTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        commitFilesLimit: Int?,
        commitsWithFilesFile: String
    ) = Assertions.assertIterableEquals(
        testService.getCommitsWithFiles(commitsWithFilesFile),
        getCommitsWithFiles(
            testService.sshUrl(group, repository),
            fromHashOrRef,
            fromDate,
            toHashOrRef,
            commitFilesLimit
        )
    )

    @ParameterizedTest
    @MethodSource("getCommitsFailsArguments")
    fun getCommitsWithFilesFailsTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            getCommitsWithFiles(testService.sshUrl(group, repository), fromHashOrRef, fromDate, toHashOrRef, null)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("getCommitArguments")
    fun getCommitTest(
        group: String,
        repository: String,
        hashOrRef: String,
        commitFile: String
    ) = Assertions.assertEquals(
        testService.getCommit(commitFile),
        getCommit(testService.sshUrl(group, repository), hashOrRef)
    )

    @ParameterizedTest
    @MethodSource("getCommitFailsArguments")
    fun getCommitFailsTest(
        group: String,
        repository: String,
        hashOrRef: String,
        exceptionClass: Class<out Throwable>
    ) {
        Assertions.assertThrows(exceptionClass) {
            getCommit(testService.sshUrl(group, repository), hashOrRef)
        }
    }

    @ParameterizedTest
    @MethodSource("getCommitWithFilesArguments")
    fun getCommitWithFilesTest(
        group: String,
        repository: String,
        hashOrRef: String,
        commitFilesLimit: Int?,
        commitWithFilesFile: String
    ) = Assertions.assertEquals(
        testService.getCommitWithFiles(commitWithFilesFile),
        getCommitWithFiles(testService.sshUrl(group, repository), hashOrRef, commitFilesLimit)
    )

    @ParameterizedTest
    @MethodSource("getCommitFailsArguments")
    fun getCommitWithFilesFailsTest(
        group: String,
        repository: String,
        hashOrRef: String,
        exceptionClass: Class<out Throwable>
    ) {
        Assertions.assertThrows(exceptionClass) {
            getCommitWithFiles(testService.sshUrl(group, repository), hashOrRef, null)
        }
    }

    @ParameterizedTest
    @MethodSource("getIssuesFromCommitsArguments")
    fun getIssuesFromCommitsTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        issuesFromCommitsFile: String
    ) = Assertions.assertEquals(
        testService.getIssuesFromCommits(issuesFromCommitsFile),
        getIssuesFromCommits(testService.sshUrl(group, repository), fromHashOrRef, fromDate, toHashOrRef)
    )

    @ParameterizedTest
    @MethodSource("getCommitsFailsArguments")
    fun getIssuesFromCommitsFailsTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            getIssuesFromCommits(testService.sshUrl(group, repository), fromHashOrRef, fromDate, toHashOrRef)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @Test
    fun getTagsTest() = Assertions.assertEquals(
        testService.getTags("tags.json"),
        getTags(testService.sshUrl(GROUP, REPOSITORY_2))
    )

    @Test
    fun getTagsFailsTest() {
        Assertions.assertThrows(NotFoundException::class.java) {
            getTags(testService.sshUrl(GROUP, "absent-repository"))
        }
    }

    @Test
    fun getTagTest() = Assertions.assertEquals(
        testService.getTag("tag.json"),
        getTag(testService.sshUrl(GROUP, REPOSITORY_2), "v1.0")
    )

    @ParameterizedTest
    @MethodSource("createTagFailsArguments")
    fun createTagFailsTest(group: String, repository: String, hashOrRef: String, exceptionClass: Class<out Throwable>) {
        Assertions.assertThrows(exceptionClass) {
            createTag(
                testService.sshUrl(group, repository),
                CreateTag("tag", hashOrRef, "createTagFailsTest")
            )
        }
    }

    @ParameterizedTest
    @MethodSource("getTagFailsArguments")
    fun getTagFailsTest(group: String, repository: String, name: String, exceptionClass: Class<out Throwable>) {
        Assertions.assertThrows(exceptionClass) {
            getTag(testService.sshUrl(group, repository), name)
        }
    }

    @ParameterizedTest
    @MethodSource("getTagFailsArguments")
    fun deleteTagFailsTest(group: String, repository: String, name: String, exceptionClass: Class<out Throwable>) {
        Assertions.assertThrows(exceptionClass) {
            deleteTag(testService.sshUrl(group, repository), name)
        }
    }

    @Test
    fun searchIssueInRangesTest() = Assertions.assertEquals(
        testService.getSearchIssueInRangesResponse("search-issue-in-ranges.json"),
        searchIssuesInRanges(
            SearchIssuesInRangesRequest(
                setOf("ISSUE-1", "ISSUE-3"),
                setOf(
                    RepositoryRange(
                        testService.sshUrl(GROUP, REPOSITORY_2),
                        null,
                        null,
                        "master"
                    ),
                    RepositoryRange(
                        testService.sshUrl(GROUP, REPOSITORY_2),
                        "7df7b682b6be1dd1e3c81ef776d5d6da44ac8ee1",
                        null,
                        "v1.0.1"
                    ),
                    RepositoryRange(
                        testService.sshUrl(GROUP, REPOSITORY_2),
                        "v1.0",
                        null,
                        "feature/ISSUE-4"
                    )
                )
            )
        )
    )

    @ParameterizedTest
    @MethodSource("getCommitsFailsArguments")
    fun searchIssueInRangesFailsTest(
        group: String,
        repository: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            searchIssuesInRanges(
                SearchIssuesInRangesRequest(
                    setOf("ISSUE-1", "ISSUE-3"),
                    setOf(
                        RepositoryRange(
                            testService.sshUrl(group, repository),
                            fromHashOrRef,
                            fromDate,
                            toHashOrRef
                        )
                    )
                )
            )
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("findByIssueKeysFailsArguments")
    fun searchIssueInRangesFailsTest2(
        issueKeys: Set<String>,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(ArgumentsNotCompatibleException::class.java) {
            searchIssuesInRanges(
                SearchIssuesInRangesRequest(
                    issueKeys,
                    setOf(
                        RepositoryRange(
                            testService.sshUrl(GROUP, REPOSITORY_2),
                            null,
                            null,
                            "master"
                        ),
                        RepositoryRange(
                            testService.sshUrl(GROUP, REPOSITORY_2),
                            "7df7b682b6be1dd1e3c81ef776d5d6da44ac8ee1",
                            null,
                            "v1.0.1"
                        ),
                        RepositoryRange(
                            testService.sshUrl(GROUP, REPOSITORY_2),
                            "v1.0",
                            null,
                            "feature/ISSUE-4"
                        )
                    )
                )
            )
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("findByIssueKeysArguments")
    fun findByIssueKeysTest(issueKeys: Set<String>, searchSummaryFile: String) = Assertions.assertEquals(
        testService.getSearchSummary(searchSummaryFile),
        findByIssueKeys(issueKeys).let { searchSummary ->
            SearchSummary(
                searchSummary.branches,
                searchSummary.commits,
                if (searchSummary.pullRequests.size > 0) {
                    SearchSummary.SearchPullRequestsSummary(
                        searchSummary.pullRequests.size,
                        Date(1698062284000L),
                        searchSummary.pullRequests.status
                    )
                } else searchSummary.pullRequests
            )
        }
    )

    @ParameterizedTest
    @MethodSource("findByIssueKeysFailsArguments")
    fun findByIssueKeysFailsTest(
        issueKeys: Set<String>,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            findByIssueKeys(issueKeys)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("findBranchesByIssueKeysArguments")
    fun findBranchesByIssueKeysTest(issueKeys: Set<String>, branchesByIssueKeyFile: String) = Assertions.assertEquals(
        testService.getBranches(branchesByIssueKeyFile),
        findBranchesByIssueKeys(issueKeys)
    )

    @ParameterizedTest
    @MethodSource("findByIssueKeysFailsArguments")
    fun findBranchesByIssueKeysFailsTest(
        issueKeys: Set<String>,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            findBranchesByIssueKeys(issueKeys)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("findCommitsByIssueKeysArguments")
    fun findCommitsByIssueKeysTest(issueKeys: Set<String>, commitsByIssueKeyFile: String) = Assertions.assertEquals(
        testService.getCommits(commitsByIssueKeyFile),
        findCommitsByIssueKeys(issueKeys)
    )

    @ParameterizedTest
    @MethodSource("findByIssueKeysFailsArguments")
    fun findCommitsByIssueKeysFailsTest(
        issueKeys: Set<String>,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            findCommitsByIssueKeys(issueKeys)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("findCommitsWithFilesByIssueKeysArguments")
    fun findCommitsWithFilesByIssueKeysTest(
        issueKeys: Set<String>,
        commitFilesLimit: Int?,
        commitsWithFilesByIssueKeyFile: String
    ) = Assertions.assertEquals(
        testService.getCommitsWithFiles(commitsWithFilesByIssueKeyFile),
        findCommitsWithFilesByIssueKeys(issueKeys, commitFilesLimit)
    )

    @ParameterizedTest
    @MethodSource("findByIssueKeysFailsArguments")
    fun findCommitsWithFilesByIssueKeysFailsTest(
        issueKeys: Set<String>,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            findCommitsWithFilesByIssueKeys(issueKeys, null)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    @ParameterizedTest
    @MethodSource("findPullRequestsByIssueKeysArguments")
    fun findPullRequestsByIssueKeysTest(issueKeys: Set<String>, pullRequestsByIssueKeyFile: String) =
        Assertions.assertEquals(
            testService.getPullRequests(pullRequestsByIssueKeyFile),
            findPullRequestsByIssueKeys(issueKeys).map { pullRequest ->
                PullRequest(
                    pullRequest.index, pullRequest.title,
                    pullRequest.description,
                    pullRequest.author,
                    pullRequest.source,
                    pullRequest.target,
                    pullRequest.assignees,
                    pullRequest.reviewers,
                    pullRequest.status,
                    Date(1698062284000L),
                    Date(1698062284000L),
                    pullRequest.link,
                    pullRequest.repository
                )
            }
        )

    @ParameterizedTest
    @MethodSource("findByIssueKeysFailsArguments")
    fun findPullRequestsByIssueKeysFailsTest(
        issueKeys: Set<String>,
        exceptionClass: Class<out Throwable>,
        exceptionMessage: String?
    ) {
        val exception = Assertions.assertThrows(exceptionClass) {
            findPullRequestsByIssueKeys(issueKeys)
        }
        if (exceptionMessage != null) {
            Assertions.assertEquals(exceptionMessage, exception.message)
        }
    }

    protected abstract fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest

    protected abstract fun getCommits(
        sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String
    ): List<Commit>

    protected abstract fun getCommitsWithFiles(
        sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String, commitFilesLimit: Int?
    ): List<CommitWithFiles>

    protected abstract fun getCommit(sshUrl: String, hashOrRef: String): Commit

    protected abstract fun getCommitWithFiles(
        sshUrl: String,
        hashOrRef: String,
        commitFilesLimit: Int?
    ): CommitWithFiles

    protected abstract fun getIssuesFromCommits(
        sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String
    ): List<String>

    protected abstract fun getTags(sshUrl: String): List<Tag>

    protected abstract fun createTag(sshUrl: String, createTag: CreateTag): Tag

    protected abstract fun getTag(sshUrl: String, name: String): Tag

    protected abstract fun deleteTag(sshUrl: String, name: String)

    protected abstract fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse

    protected abstract fun findByIssueKeys(issueKeys: Set<String>): SearchSummary

    protected abstract fun findBranchesByIssueKeys(issueKeys: Set<String>): List<Branch>

    protected abstract fun findCommitsByIssueKeys(issueKeys: Set<String>): List<Commit>

    protected abstract fun findCommitsWithFilesByIssueKeys(
        issueKeys: Set<String>,
        commitFilesLimit: Int?
    ): List<CommitWithFiles>

    protected abstract fun findPullRequestsByIssueKeys(issueKeys: Set<String>): List<PullRequest>


    companion object {
        const val BITBUCKET_USER = "admin"
        const val BITBUCKET_PASSWORD = "admin"

        const val GITEA_USER = "test-admin"
        const val GITEA_PASSWORD = "test-admin"

        const val GROUP = "test"
        const val REPOSITORY = "repository"
        const val REPOSITORY_2 = "repository-2"

        //<editor-fold defaultstate="collapsed" desc="test parameters">
        @JvmStatic
        private fun createPullRequestFailsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                "absent-repository",
                CreatePullRequest(
                    "branch16", "master", "Test PR title 2", "Test PR description 2"
                ),
                NotFoundException::class.java
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                CreatePullRequest(
                    "absent-ref", "master", "Test PR title 3", "Test PR description 3"
                ),
                NotFoundException::class.java
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                CreatePullRequest(
                    "branch16", "absent-ref", "Test PR title 4", "Test PR description 4"
                ),
                NotFoundException::class.java
            )
        )

        @JvmStatic
        private fun getCommitsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY,
                null,
                Date(1698062284000L),
                "5fb773dbe6472a87632b1c68ea771decdcd20f1e",
                "commits.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                "c79babff3c1405618214eba90398c685ac4c0349",
                null,
                "5fb773dbe6472a87632b1c68ea771decdcd20f1e",
                "commits-2.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                "branch8",
                null,
                "branch6",
                "commits-3.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                null,
                null,
                "master",
                "commits-4.json"
            )
        )

        @JvmStatic
        private fun getCommitsFailsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY,
                "master",
                Date(),
                "master",
                ArgumentsNotCompatibleException::class.java,
                "'hashOrRef' and 'date' can not be used together"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                "9c8daf84e7ed6ea1d32f654362131e26dbf37440",
                null,
                "a933dc6b4fe8e8f66856b4cb9da7f4bf8ad0e017",
                NotFoundException::class.java,
                "Cannot find commit '9c8daf84e7ed6ea1d32f654362131e26dbf37440' in commit graph for commit 'a933dc6b4fe8e8f66856b4cb9da7f4bf8ad0e017' in 'test:repository'"
            ),
            Arguments.of(
                GROUP,
                "absent-repository",
                null,
                null,
                "master",
                NotFoundException::class.java,
                null
            ),
            Arguments.of(
                GROUP,
                REPOSITORY,
                null,
                null,
                "absent-ref",
                NotFoundException::class.java,
                null
            )
        )

        @JvmStatic
        private fun getCommitsWithFilesArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                null,
                null,
                "master",
                null,
                "commits-with-files.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                null,
                null,
                "master",
                1,
                "commits-with-files-2.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "7df7b682b6be1dd1e3c81ef776d5d6da44ac8ee1",
                null,
                "v1.0.1",
                -1,
                "commits-with-files-3.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "v1.0",
                null,
                "feature/ISSUE-4",
                null,
                "commits-with-files-4.json"
            )
        )

        @JvmStatic
        private fun getCommitArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "fa20861b90c54efbffeb48837f4044bc23b55238",
                "commit.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "bugfix/ISSUE-3",
                "commit-2.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "v1.0",
                "commit-3.json"
            ),
        )

        @JvmStatic
        private fun getCommitFailsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "invalid-commit-hash",
                NotFoundException::class.java
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "0123456789abcdef0123456789abcdef01234567",
                NotFoundException::class.java
            ),
            Arguments.of(
                GROUP,
                "absent-repository",
                "0123456789abcdef0123456789abcdef01234567",
                NotFoundException::class.java
            ),
        )

        @JvmStatic
        private fun getCommitWithFilesArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "fa20861b90c54efbffeb48837f4044bc23b55238",
                1,
                "commit-with-files.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "bugfix/ISSUE-3",
                -1,
                "commit-with-files-2.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "v1.0",
                null,
                "commit-with-files-3.json"
            ),
        )

        @JvmStatic
        private fun createTagFailsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "absent-hash-or-ref",
                NotFoundException::class.java
            ),
            Arguments.of(
                GROUP,
                "absent-repository",
                "absent-hash-or-ref",
                NotFoundException::class.java
            ),
        )

        @JvmStatic
        private fun getTagFailsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "absent-tag",
                NotFoundException::class.java
            ),
            Arguments.of(
                GROUP,
                "absent-repository",
                "absent-tag",
                NotFoundException::class.java
            ),
        )

        @JvmStatic
        private fun getIssuesFromCommitsArguments() = Stream.of(
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                null,
                null,
                "master",
                "issues-from-commits.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "7df7b682b6be1dd1e3c81ef776d5d6da44ac8ee1",
                null,
                "v1.0.1",
                "issues-from-commits-2.json"
            ),
            Arguments.of(
                GROUP,
                REPOSITORY_2,
                "v1.0",
                null,
                "feature/ISSUE-4",
                "issues-from-commits-3.json"
            )
        )

        @JvmStatic
        private fun findByIssueKeysArguments() = Stream.of(
            Arguments.of(setOf("ISSUE-1"), "search-summary.json"),
            Arguments.of(setOf("ISSUE-2"), "search-summary-2.json"),
            Arguments.of(setOf("ISSUE-3"), "search-summary-3.json"),
            Arguments.of(setOf("ISSUE-4"), "search-summary-4.json"),
            Arguments.of(setOf("ISSUE-5"), "search-summary-5.json"),
            Arguments.of(setOf("ISSUE-6"), "search-summary-6.json"),
            Arguments.of(setOf("ISSUE-7"), "search-summary-7.json"),
            Arguments.of(
                setOf("ISSUE-1", "ISSUE-2", "ISSUE-3", "ISSUE-4", "ISSUE-5", "ISSUE-6", "ISSUE-7"),
                "search-summary-8.json"
            )
        )

        @JvmStatic
        private fun findByIssueKeysFailsArguments() = Stream.of(
            Arguments.of(
                setOf("ISSUE"),
                ArgumentsNotCompatibleException::class.java,
                "Invalid issue keys: 'ISSUE'"
            ),
            Arguments.of(
                setOf("ISSUE-1", "ISSUE-2A", " ISSUE-3", "ISSUE+4", "0ISSUE-5"),
                ArgumentsNotCompatibleException::class.java,
                "Invalid issue keys: ' ISSUE-3', '0ISSUE-5', 'ISSUE+4', 'ISSUE-2A'"
            )
        )

        @JvmStatic
        private fun findBranchesByIssueKeysArguments() = Stream.of(
            Arguments.of(setOf("ISSUE-1"), "branches-by-issue-keys.json"),
            Arguments.of(setOf("ISSUE-2"), "branches-by-issue-keys-2.json"),
            Arguments.of(setOf("ISSUE-3"), "branches-by-issue-keys-3.json"),
            Arguments.of(setOf("ISSUE-4"), "branches-by-issue-keys-4.json"),
            Arguments.of(setOf("ISSUE-5"), "branches-by-issue-keys-5.json"),
            Arguments.of(setOf("ISSUE-6"), "branches-by-issue-keys-6.json"),
            Arguments.of(setOf("ISSUE-7"), "branches-by-issue-keys-7.json"),
            Arguments.of(
                setOf("ISSUE-1", "ISSUE-2", "ISSUE-3", "ISSUE-4", "ISSUE-5", "ISSUE-6", "ISSUE-7"),
                "branches-by-issue-keys-8.json"
            )
        )

        @JvmStatic
        private fun findCommitsByIssueKeysArguments() = Stream.of(
            Arguments.of(setOf("ISSUE-1"), "commits-by-issue-keys.json"),
            Arguments.of(setOf("ISSUE-2"), "commits-by-issue-keys-2.json"),
            Arguments.of(setOf("ISSUE-3"), "commits-by-issue-keys-3.json"),
            Arguments.of(setOf("ISSUE-4"), "commits-by-issue-keys-4.json"),
            Arguments.of(setOf("ISSUE-5"), "commits-by-issue-keys-5.json"),
            Arguments.of(setOf("ISSUE-6"), "commits-by-issue-keys-6.json"),
            Arguments.of(setOf("ISSUE-7"), "commits-by-issue-keys-7.json"),
            Arguments.of(
                setOf("ISSUE-1", "ISSUE-2", "ISSUE-3", "ISSUE-4", "ISSUE-5", "ISSUE-6", "ISSUE-7"),
                "commits-by-issue-keys-8.json"
            )
        )

        @JvmStatic
        private fun findCommitsWithFilesByIssueKeysArguments() = Stream.of(
            Arguments.of(setOf("ISSUE-1"), null, "commits-with-files-by-issue-keys.json"),
            Arguments.of(setOf("ISSUE-2"), -1, "commits-with-files-by-issue-keys-2.json"),
            Arguments.of(setOf("ISSUE-3"), 1, "commits-with-files-by-issue-keys-3.json"),
            Arguments.of(setOf("ISSUE-4"), 2, "commits-with-files-by-issue-keys-4.json"),
            Arguments.of(setOf("ISSUE-5"), 0, "commits-with-files-by-issue-keys-5.json"),
            Arguments.of(setOf("ISSUE-6"), 0, "commits-with-files-by-issue-keys-6.json"),
            Arguments.of(setOf("ISSUE-7"), 0, "commits-with-files-by-issue-keys-7.json"),
            Arguments.of(
                setOf("ISSUE-1", "ISSUE-2", "ISSUE-3", "ISSUE-4", "ISSUE-5", "ISSUE-6", "ISSUE-7"),
                0,
                "commits-with-files-by-issue-keys-8.json"
            )
        )

        @JvmStatic
        private fun findPullRequestsByIssueKeysArguments() = Stream.of(
            Arguments.of(setOf("ISSUE-1"), "pull-requests-by-issue-keys.json"),
            Arguments.of(setOf("ISSUE-2"), "pull-requests-by-issue-keys-2.json"),
            Arguments.of(setOf("ISSUE-3"), "pull-requests-by-issue-keys-3.json"),
            Arguments.of(setOf("ISSUE-4"), "pull-requests-by-issue-keys-4.json"),
            Arguments.of(setOf("ISSUE-5"), "pull-requests-by-issue-keys-5.json"),
            Arguments.of(setOf("ISSUE-6"), "pull-requests-by-issue-keys-6.json"),
            Arguments.of(setOf("ISSUE-7"), "pull-requests-by-issue-keys-7.json"),
            Arguments.of(
                setOf("ISSUE-1", "ISSUE-2", "ISSUE-3", "ISSUE-4", "ISSUE-5", "ISSUE-6", "ISSUE-7"),
                "pull-requests-by-issue-keys-8.json"
            )
        )
        //</editor-fold>
    }
}
