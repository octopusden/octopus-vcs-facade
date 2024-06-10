package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.BeforeAll
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.Configuration
import org.octopusden.octopus.vcsfacade.GITEA
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == '$GITEA'] == '$GITEA'}", loadContext = true)
class VcsFacadeUnitTestGitea : BaseVcsFacadeUnitTest(
    TestService.Gitea(Configuration.model.gitea.host),
    GiteaTestClient(
        Configuration.model.gitea.url,
        Configuration.model.gitea.user,
        Configuration.model.gitea.password
    )
) {
    @BeforeAll
    fun beforeAllVcsFacadeUnitTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
    }
}
