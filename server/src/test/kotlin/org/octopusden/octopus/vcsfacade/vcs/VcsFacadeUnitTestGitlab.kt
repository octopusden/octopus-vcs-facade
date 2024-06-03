package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.infrastructure.gitlab.test.GitlabTestClient
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'gitlab'] == 'gitlab'}", loadContext = true)
class VcsFacadeUnitTestGitlab : BaseVcsFacadeUnitTest(
    TestService.Gitlab(GITLAB_HOST),
    GitlabTestClient("http://$GITLAB_HOST", GITLAB_USER, GITLAB_PASSWORD)
)
