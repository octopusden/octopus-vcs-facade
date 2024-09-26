package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.BeforeAll
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'gitea'] == 'gitea'}", loadContext = true)
class VcsFacadeUnitTestGitea : BaseVcsFacadeUnitTest(
    TestService.Gitea(vcsFacadeHost, vcsHost),
    GiteaTestClient("http://$vcsHost", GITEA_USER, GITEA_PASSWORD)
) {
    @BeforeAll
    fun beforeAllVcsFacadeUnitTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY_2)
    }
}
