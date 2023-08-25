package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient

private const val vcsHost = "localhost:7990"

class RepositoryControllerBitbucketTest : BaseRepositoryControllerTest(
    vcsHost,
    BitbucketTestClient(vcsHost, BITBUCKET_USER, BITBUCKET_PASSWORD),
    "ssh://git@$vcsHost/%s/%s.git"
)
