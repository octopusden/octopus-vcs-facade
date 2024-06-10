package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient

@EnabledIfSystemProperty(named = TEST_PROFILE, matches = GITLAB)
class VcsFacadeFunctionalTestGitlab : BaseVcsFacadeFunctionalTest(
    TestService.Gitlab(
        Configuration.model.gitlab.host,
        Configuration.model.gitlab.externalHost
    ),
    GitlabTestClient(
        Configuration.model.gitlab.url,
        Configuration.model.gitlab.user,
        Configuration.model.gitlab.password,
        Configuration.model.gitlab.externalHost
    )
)
