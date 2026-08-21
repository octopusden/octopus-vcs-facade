package org.octopusden.octopus.vcsfacade.vcs

import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketUpdateRepository
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'bitbucket'] == 'bitbucket'}", loadContext = true)
class VcsFacadeUnitTestBitbucket :
    BaseVcsFacadeUnitTest(
        TestService.Bitbucket(vcsFacadeHost, vcsHost),
        BitbucketTestClient("http://$vcsHost", BITBUCKET_USER, BITBUCKET_PASSWORD),
    ) {
    override fun archiveRepository(repository: String) {
        val client = BitbucketClassicClient(object : BitbucketClientParametersProvider {
            override fun getApiUrl() = "http://$vcsHost"

            override fun getAuth(): BitbucketCredentialProvider = BitbucketBasicCredentialProvider(BITBUCKET_USER, BITBUCKET_PASSWORD)
        })
        val bitbucketRepository = client.getRepository(GROUP, repository)
        client.updateRepository(
            GROUP,
            repository,
            BitbucketUpdateRepository(bitbucketRepository.name, bitbucketRepository.project, archived = true),
        )
    }
}
