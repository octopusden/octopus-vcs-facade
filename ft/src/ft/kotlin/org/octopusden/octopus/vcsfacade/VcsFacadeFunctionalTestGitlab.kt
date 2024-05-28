package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient

@EnabledIfSystemProperty(named = "test.profile", matches = "gitlab")
class VcsFacadeFunctionalTestGitlab : BaseVcsFacadeFunctionalTest(
    TestService.Gitlab(GITLAB_HOST, GITLAB_EXTERNAL_HOST),
    GitlabTestClient("http://$GITLAB_HOST", GITLAB_USER, GITLAB_PASSWORD, GITLAB_EXTERNAL_HOST)
)
