package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import org.octopusden.octopus.vcsfacade.Configuration
import org.octopusden.octopus.vcsfacade.GITLAB
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == '$GITLAB'] == '$GITLAB'}", loadContext = true)
class VcsFacadeUnitTestGitlab : BaseVcsFacadeUnitTest(
    TestService.Gitlab(Configuration.model.gitlab.host),
    GitlabTestClient(
        Configuration.model.gitlab.url,
        Configuration.model.gitlab.user,
        Configuration.model.gitlab.password
    )
)
