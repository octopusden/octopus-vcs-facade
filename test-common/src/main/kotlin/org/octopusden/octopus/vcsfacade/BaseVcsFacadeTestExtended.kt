package org.octopusden.octopus.vcsfacade

import java.io.File
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseVcsFacadeTestExtended(
    testService: TestService, testClient: TestClient
) : BaseVcsFacadeTest(testService, testClient) {
    @Test
    fun tagsTestScenario() {
        val repository = "repository-2-tags"
        testClient.importRepository(
            testService.sshUrl(GROUP, repository),
            File.createTempFile("BaseVcsFacadeTest-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTestExtended::class.java.classLoader.getResourceAsStream("$GROUP-$REPOSITORY_2.zip")!!
                        .copyTo(it)
                }
            }
        )
        createTag(
            testService.sshUrl(GROUP, repository),
            CreateTag("test-0.1", "v1.0", "tagsTestScenario")
        )
        createTag(
            testService.sshUrl(GROUP, repository),
            CreateTag("test-0.2", "d25d71af3afa700e91a1613c5ab4ec6b26a88ff7", "tagsTestScenario")
        )
        createTag(
            testService.sshUrl(GROUP, repository),
            CreateTag("test-0.3", "feature/ISSUE-4", "tagsTestScenario")
        )
        deleteTag(testService.sshUrl(GROUP, repository), "v1.0")
        Assertions.assertEquals(
            testService.getTags("tags-scenario.json"), getTags(testService.sshUrl(GROUP, repository), null)
        )
    }
}
