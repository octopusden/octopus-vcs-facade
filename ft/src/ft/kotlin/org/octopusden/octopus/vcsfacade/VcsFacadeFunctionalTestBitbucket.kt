package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

@EnabledIfSystemProperty(named = "test.profile", matches = "bitbucket")
class VcsFacadeFunctionalTestBitbucket : BaseVcsFacadeFunctionalTest(
    TestService.Bitbucket(BITBUCKET_HOST, BITBUCKET_EXTERNAL_HOST),
    BitbucketTestClient("http://$BITBUCKET_HOST", BITBUCKET_USER, BITBUCKET_PASSWORD, BITBUCKET_EXTERNAL_HOST)
)
