package org.octopusden.octopus.vcsfacade

import java.io.File
import java.util.Date
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.exception.ArgumentsNotCompatibleException
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTest(
    private val testService: TestService,
    private val testClient: TestClient
) {
    @BeforeAll
    fun beforeAll() {
        testClient.importRepository(
            testService.sshUrl(GROUP, REPOSITORY),
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$REPOSITORY").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader.getResourceAsStream("$GROUP-$REPOSITORY.zip")!!.copyTo(it)
                }
            }
        )
    }

    @AfterAll
    fun afterAll() {
        testClient.clearData()
    }

    @ParameterizedTest
    @MethodSource("getCommitsArguments")
    fun getCommits(
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
    fun getCommitsFails(
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

    protected abstract fun getCommits(
        sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String
    ): List<Commit>

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
    }
}

