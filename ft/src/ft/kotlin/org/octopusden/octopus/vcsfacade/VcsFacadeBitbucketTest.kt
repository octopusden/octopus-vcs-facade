package org.octopusden.octopus.vcsfacade

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

private const val vcsHost = "bitbucket:8990"

class VcsFacadeBitbucketTest : BaseVcsFacadeFuncTest(
    vcsHost,
    BitbucketTestClient(vcsHost, BITBUCKET_USER, BITBUCKET_PASSWORD),
    "ssh://git@$vcsHost/%s/%s.git"
)
