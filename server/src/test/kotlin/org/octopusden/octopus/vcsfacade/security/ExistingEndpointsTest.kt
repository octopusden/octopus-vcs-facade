package org.octopusden.octopus.vcsfacade.security

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.Repository
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag
import org.octopusden.octopus.vcsfacade.config.JobConfig
import org.octopusden.octopus.vcsfacade.controller.ExceptionInfoHandler
import org.octopusden.octopus.vcsfacade.controller.ProvisioningController
import org.octopusden.octopus.vcsfacade.controller.ProvisioningExceptionHandler
import org.octopusden.octopus.vcsfacade.controller.RepositoryController
import org.octopusden.octopus.vcsfacade.service.ProvisioningTargetResolver
import org.octopusden.octopus.vcsfacade.service.RequestProcessor
import org.octopusden.octopus.vcsfacade.service.VcsManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.lang.reflect.Type

/**
 * The endpoints that existed before provisioning must answer the same without a token, whatever the setting.
 */
abstract class ExistingEndpointsTest {
    /**
     * The web layer and security of the service without its VCS clients, the index or a database.
     * Every class here switches itself on or off from `vcs-facade.provisioning.enabled`, as in the application.
     * Nested in the test so the full-context tests' component scan skips it.
     */
    @Configuration
    @Import(
        ProvisioningController::class,
        RepositoryController::class,
        ExceptionInfoHandler::class,
        ProvisioningExceptionHandler::class,
        ProvisioningTargetResolver::class,
        ProvisioningPermissionEvaluator::class,
        WebSecurityConfig::class,
        AnonymousSecurityConfig::class,
    )
    class WebTestConfig {
        @Bean
        fun jobProperties() = JobConfig.JobProperties(fastWorkTimoutSecs = 30, retryIntervalSecs = 1, executor = null)

        @Bean
        fun jobExecutor() = ThreadPoolTaskExecutor().apply { initialize() }

        // Runs each request in place: these tests are about security, not about deferred results.
        @Bean
        fun requestProcessor() =
            object : RequestProcessor {
                override fun <T : Any> process(
                    requestId: String,
                    type: Type,
                    func: () -> T,
                ): T = func()
            }
    }

    @Autowired
    protected lateinit var mvc: MockMvc

    @MockBean
    protected lateinit var vcsManager: VcsManager

    private val repository = Repository("ssh://git@host/g/r.git", "http://host/g/r")

    @Test
    fun `an existing GET answers without a token`() {
        `when`(vcsManager.getRepository(anyString())).thenReturn(repository)
        mvc
            .perform(get("/rest/api/2/repository").param("sshUrl", repository.sshUrl).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sshUrl").value(repository.sshUrl))
            .andExpect(header().doesNotExist("WWW-Authenticate"))
    }

    @Test
    fun `an existing POST answers without a token and without a CSRF token`() {
        `when`(vcsManager.createTag(anyString(), any(CreateTag::class.java) ?: CreateTag("", "", "")))
            .thenReturn(Tag("v1", "abc", "http://host/tag", repository))
        mvc
            .perform(
                post("/rest/api/2/repository/tags")
                    .param("sshUrl", repository.sshUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"v1","hashOrRef":"master","message":"m"}""")
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("v1"))
    }

    @Test
    fun `an existing DELETE answers without a token and without a CSRF token`() {
        mvc
            .perform(delete("/rest/api/2/repository/tag").param("sshUrl", repository.sshUrl).param("name", "v1"))
            .andExpect(status().isOk)
    }
}
