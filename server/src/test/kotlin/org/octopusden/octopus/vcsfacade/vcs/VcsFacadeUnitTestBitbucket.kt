package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf


@EnabledIf("#{environment.getActiveProfiles().$[#this == 'bitbucket'] == 'bitbucket'}", loadContext = true)
class VcsFacadeUnitTestBitbucket : BaseVcsFacadeUnitTest(
    TestService.Bitbucket(BITBUCKET_HOST),
    BitbucketTestClient("http://$BITBUCKET_HOST", BITBUCKET_USER, BITBUCKET_PASSWORD)
)
