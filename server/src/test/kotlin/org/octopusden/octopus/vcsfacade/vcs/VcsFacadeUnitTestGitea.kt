package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClassicClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaEditRepoOption
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.BaseVcsFacadeTest
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf
import java.io.File

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'gitea'] == 'gitea'}", loadContext = true)
class VcsFacadeUnitTestGitea :
    BaseVcsFacadeUnitTest(
        TestService.Gitea(vcsFacadeHost, vcsHost),
        GiteaTestClient("http://$vcsHost", GITEA_USER, GITEA_PASSWORD),
    ) {
    @BeforeAll
    fun beforeAllVcsFacadeUnitTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
    }

    @Test
    fun getArchivedRepositoryTest() {
        val repository = "repository-5-archived"
        testClient.importRepository(
            testService.sshUrl(GROUP, repository),
            File.createTempFile("VcsFacadeUnitTestGitea-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader
                        .getResourceAsStream("$GROUP-$REPOSITORY.zip")!!
                        .copyTo(it)
                }
            },
        )
        val client = GiteaClassicClient(object : ClientParametersProvider {
            override fun getApiUrl() = "http://$vcsHost"

            override fun getAuth() = StandardBasicCredCredentialProvider(GITEA_USER, GITEA_PASSWORD)
        })
        client.updateRepositoryConfiguration(GROUP, repository, GiteaEditRepoOption(archived = true))

        Assertions.assertEquals(true, getRepository(testService.sshUrl(GROUP, repository)).archived)
    }
}
