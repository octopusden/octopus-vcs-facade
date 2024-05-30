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
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTest(
    protected val testService: TestService,
    private val testClient: TestClient
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
    }

    @AfterAll
    fun afterAllBaseVcsFacadeTest() {
        testClient.clearData()
    }

    @Test
    fun createPullRequestTest() {
        val createPullRequest = CreatePullRequest("branch16", "master", "Test PR title", "Test PR description")
        val pullRequest = createPullRequest(testService.sshUrl(GROUP, REPOSITORY), createPullRequest)
        Assertions.assertEquals(createPullRequest.sourceBranch, pullRequest.source)
        Assertions.assertEquals(createPullRequest.targetBranch, pullRequest.target)
        Assertions.assertEquals(createPullRequest.title, pullRequest.title)
        Assertions.assertEquals(createPullRequest.description, pullRequest.description)
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

    companion object {
        const val BITBUCKET_HOST = "localhost:7990"
        const val BITBUCKET_USER = "admin"
        const val BITBUCKET_PASSWORD = "admin"
        const val BITBUCKET_EXTERNAL_HOST = "bitbucket:7990"

        const val GITEA_HOST = "localhost:3000"
        const val GITEA_USER = "test-admin"
        const val GITEA_PASSWORD = "test-admin"
        const val GITEA_EXTERNAL_HOST = "gitea:3000"

        const val GITLAB_HOST = "localhost:8990"
        const val GITLAB_USER = "root"
        const val GITLAB_PASSWORD = "VomkaEa6PD1OIgY7dQVbPUuO8wi9RMCaZw/i9yPXcI0="
        const val GITLAB_EXTERNAL_HOST = "gitlab:8990"

        const val GROUP = "test"
        const val REPOSITORY = "repository"
        const val REPOSITORY_2 = "repository-2"

        //<editor-fold defaultstate="collapsed" desc="test parameters">
        @JvmStatic
        private fun createPullRequestFailsArguments(): Stream<Arguments> = Stream.of(
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
        private fun getCommitsArguments(): Stream<Arguments> = Stream.of(
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
        private fun getCommitsFailsArguments(): Stream<Arguments> = Stream.of(
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
        private fun getCommitsWithFilesArguments(): Stream<Arguments> = Stream.of(
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
        private fun getCommitArguments(): Stream<Arguments> = Stream.of(
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
        private fun getCommitFailsArguments(): Stream<Arguments> = Stream.of(
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
        private fun getCommitWithFilesArguments(): Stream<Arguments> = Stream.of(
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
        //</editor-fold>
    }
}

