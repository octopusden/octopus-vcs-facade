package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

@EnabledIfSystemProperty(named = "test.profile", matches = "bitbucket")
class VcsFacadeFunctionalTestBitbucket : BaseVcsFacadeFunctionalTest(
    TestService.Bitbucket(vcsFacadeHost, vcsExternalHost),
    BitbucketTestClient("http://$vcsHost", BITBUCKET_USER, BITBUCKET_PASSWORD, vcsExternalHost)
)
