package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient
import org.octopusden.octopus.vcsfacade.BITBUCKET
import org.octopusden.octopus.vcsfacade.Configuration
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf


@EnabledIf("#{environment.getActiveProfiles().$[#this == '$BITBUCKET'] == '$BITBUCKET'}", loadContext = true)
class VcsFacadeUnitTestBitbucket : BaseVcsFacadeUnitTest(
    TestService.Bitbucket(Configuration.model.bitbucket.host),
    BitbucketTestClient(
        Configuration.model.bitbucket.url,
        Configuration.model.bitbucket.user,
        Configuration.model.bitbucket.password
    )
)
