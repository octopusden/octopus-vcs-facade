package org.octopusden.octopus.vcsfacade.vcs

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.octopusden.octopus.infrastructure.common.test.TestClient
import org.octopusden.octopus.vcsfacade.BaseVcsFacadeTest
import org.octopusden.octopus.vcsfacade.TestService
import org.octopusden.octopus.vcsfacade.VcsFacadeApplication
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@AutoConfigureMockMvc
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = [VcsFacadeApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseVcsFacadeUnitTest(
    testService: TestService, testClient: TestClient
) : BaseVcsFacadeTest(testService, testClient) {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeAll
    fun beforeAllRepositoryControllerTests() {
        objectMapper.setLocale(Locale.ENGLISH)
    }

    override fun getCommits(
        sshUrl: String,
        fromHashOrRef: String?,
        fromDate: Date?,
        toHashOrRef: String
    ) = mvc.perform(
        MockMvcRequestBuilders.get("/rest/api/2/repository/commits")
            .param("sshUrl", sshUrl)
            .param("fromHashOrRef", fromHashOrRef)
            .param("fromDate", fromDate?.toVcsFacadeFormat())
            .param("toHashOrRef", toHashOrRef)
            .accept(MediaType.APPLICATION_JSON)
    ).andReturn().response.toObject(object : TypeReference<List<Commit>>() {})


    private fun <T> MockHttpServletResponse.toObject(typeReference: TypeReference<T>): T {
        if (status / 100 != 2) {
            throw try {
                objectMapper.readValue(this.contentAsByteArray, ErrorResponse::class.java).let {
                    it.errorCode.getException(it.errorMessage)
                }
            } catch (e: Exception) {
                RuntimeException(String(this.contentAsByteArray))
            }
        }
        return objectMapper.readValue(this.contentAsByteArray, typeReference)
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault())

        private fun Date.toVcsFacadeFormat(): String {
            return DATE_FORMATTER.format(toInstant())
        }
    }
}
