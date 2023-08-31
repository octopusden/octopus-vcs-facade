package org.octopusden.octopus.vcsfacade

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

class VcsFacadeBitbucketTest : BaseVcsFacadeFuncTest(
    BitbucketTestClient("localhost:7990", BITBUCKET_USER, BITBUCKET_PASSWORD),
    "ssh://git@bitbucket:7990/%s/%s.git"
)
