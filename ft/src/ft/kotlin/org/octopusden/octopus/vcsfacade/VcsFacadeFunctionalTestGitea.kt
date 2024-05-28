package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infrastructure.gitea.test.GiteaTestClient

@EnabledIfSystemProperty(named = "test.profile", matches = "gitea")
class VcsFacadeFunctionalTestGitea : BaseVcsFacadeFunctionalTest(
    TestService.Gitea(GITEA_HOST, GITEA_EXTERNAL_HOST, true),
    GiteaTestClient("http://$GITEA_HOST", GITEA_USER, GITEA_PASSWORD, GITEA_EXTERNAL_HOST)
) {
    @BeforeAll
    fun beforeAllVcsFacadeUnitTestGitea() {
        (testService as TestService.Gitea).scan(GROUP, REPOSITORY)
    }
}
