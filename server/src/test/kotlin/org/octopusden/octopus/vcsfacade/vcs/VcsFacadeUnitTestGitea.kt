package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.BeforeAll
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBasicCredCredentialProvider
import org.octopusden.octopus.infrastructure.gitea.client.GiteaClassicClient
import org.octopusden.octopus.infrastructure.gitea.client.dto.GiteaEditRepoOption
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf

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

    override fun archiveRepository(repository: String) {
        val client = GiteaClassicClient(object : ClientParametersProvider {
            override fun getApiUrl() = "http://$vcsHost"

            override fun getAuth() = StandardBasicCredCredentialProvider(GITEA_USER, GITEA_PASSWORD)
        })
        client.updateRepositoryConfiguration(GROUP, repository, GiteaEditRepoOption(archived = true))
    }
}
