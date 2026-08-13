package org.octopusden.octopus.vcsfacade.vcs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.octopusden.octopus.infastructure.bitbucket.test.BitbucketTestClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketBasicCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClassicClient
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketClientParametersProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.BitbucketCredentialProvider
import org.octopusden.octopus.infrastructure.bitbucket.client.dto.BitbucketUpdateRepository
import org.octopusden.octopus.vcsfacade.BaseVcsFacadeTest
import org.octopusden.octopus.vcsfacade.TestService
import org.springframework.test.context.junit.jupiter.EnabledIf
import java.io.File

@EnabledIf("#{environment.getActiveProfiles().$[#this == 'bitbucket'] == 'bitbucket'}", loadContext = true)
class VcsFacadeUnitTestBitbucket :
    BaseVcsFacadeUnitTest(
        TestService.Bitbucket(vcsFacadeHost, vcsHost),
        BitbucketTestClient("http://$vcsHost", BITBUCKET_USER, BITBUCKET_PASSWORD),
    ) {

    @Test
    fun getArchivedRepositoryTest() {
        val repository = "repository-5-archived"
        testClient.importRepository(
            testService.sshUrl(GROUP, repository),
            File.createTempFile("VcsFacadeUnitTestBitbucket-", "-$GROUP-$repository").apply {
                outputStream().use {
                    BaseVcsFacadeTest::class.java.classLoader
                        .getResourceAsStream("$GROUP-$REPOSITORY.zip")!!
                        .copyTo(it)
                }
            },
        )
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

        Assertions.assertEquals(true, getRepository(testService.sshUrl(GROUP, repository)).archived)
    }
}
