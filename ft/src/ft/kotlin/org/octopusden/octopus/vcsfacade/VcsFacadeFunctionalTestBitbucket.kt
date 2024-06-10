package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

@EnabledIfSystemProperty(named = TEST_PROFILE, matches = BITBUCKET)
class VcsFacadeFunctionalTestBitbucket : BaseVcsFacadeFunctionalTest(
    TestService.Bitbucket(Configuration.model.bitbucket.host, Configuration.model.bitbucket.externalHost),
    BitbucketTestClient(
        Configuration.model.bitbucket.url,
        Configuration.model.bitbucket.user,
        Configuration.model.bitbucket.password,
        Configuration.model.bitbucket.externalHost
    )
)
