package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.exception.NotFoundException
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTestExtended(
    testService: TestService,
    testClient: TestClient,
) : BaseVcsFacadeTest(testService, testClient) {
    protected abstract fun getRepository(sshUrl: String): Repository

    @Test
    fun getRepositoryTest() {
        val repository = "repository-3-repository"
        testClient.importRepository(
            testService.sshUrl(GROUP, repository),
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTestExtended::class.java.classLoader
                        .getResourceAsStream("$GROUP-$REPOSITORY.zip")!!
                        .copyTo(it)
                }
            },
        )
        val result = getRepository(testService.sshUrl(GROUP, repository))
        Assertions.assertEquals(testService.sshUrl(GROUP, repository), result.sshUrl)
        Assertions.assertEquals(false, result.archived)
    }

    @Test
    fun getArchivedRepositoryTest() {
        val repository = "repository-5-archived"
        val sshUrl = testService.sshUrl(GROUP, repository)
        testClient.importRepository(
            sshUrl,
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTestExtended::class.java.classLoader
                        .getResourceAsStream("$GROUP-$REPOSITORY.zip")!!
                        .copyTo(it)
                }
            },
        )
        testClient.setArchived(sshUrl, true)
        try {
            Assertions.assertEquals(true, getRepository(sshUrl).archived)
        } finally {
            testClient.setArchived(sshUrl, false)
        }
    }

    @Test
    fun getRepositoryFailsTest() {
        Assertions.assertThrows(NotFoundException::class.java) {
            getRepository(testService.sshUrl(GROUP, "absent-repository"))
        }
    }

    @Test
    fun tagsTestScenario() {
        val repository = "repository-2-tags"
        testClient.importRepository(
            testService.sshUrl(GROUP, repository),
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTestExtended::class.java.classLoader
                        .getResourceAsStream("$GROUP-$REPOSITORY_2.zip")!!
                        .copyTo(it)
                }
            },
        )
        createTag(
            testService.sshUrl(GROUP, repository),
            CreateTag("test-0.1", "v1.0", "tagsTestScenario"),
        )
        createTag(
            testService.sshUrl(GROUP, repository),
            CreateTag("test-0.2", "d25d71af3afa700e91a1613c5ab4ec6b26a88ff7", "tagsTestScenario"),
        )
        createTag(
            testService.sshUrl(GROUP, repository),
            CreateTag("test-0.3", "feature/ISSUE-4", "tagsTestScenario"),
        )
        deleteTag(testService.sshUrl(GROUP, repository), "v1.0")
        Assertions.assertEquals(
            testService.getTags("tags-scenario.json"),
            getTags(testService.sshUrl(GROUP, repository), null),
        )
    }
}
