package org.octopusden.octopus.vcsfacade.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.octopusden.octopus.vcsfacade.config.VCSConfig
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.exception.IndexerDisabledException
import org.octopusden.octopus.vcsfacade.exception.InvalidSignatureException
import org.octopusden.octopus.vcsfacade.service.OpenSearchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("indexer/gitea")
@ConditionalOnProperty(
    prefix = "vcs-facade.vcs.gitea",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class GiteaIndexerController(
    giteaProperties: VCSConfig.GiteaProperties,
    private val openSearchService: OpenSearchService?,
    private val objectMapper: ObjectMapper
) {
    private val mac = giteaProperties.webhookSecret?.let {
        Mac.getInstance(MAC_ALGORITHM).apply {
            this.init(SecretKeySpec(it.toByteArray(), MAC_ALGORITHM))
        }
    }
        get() = field?.clone() as Mac?

    @PostMapping
    fun processWebhookEvent(
        @RequestHeader("x-gitea-event-type") eventType: String,
        @RequestHeader("x-gitea-event") event: String,
        @RequestHeader("x-gitea-signature") signature: String?,
        request: HttpServletRequest
    ) {
        log.debug("Receive webhook event {}:{}", eventType, event)
        val payload = request.inputStream.use { it.readAllBytes() }
        mac?.let {
            if (signature == null) {
                throw InvalidSignatureException("Signature is absent")
            } else {
                val calculatedSignature = StringBuilder()
                it.doFinal(payload).forEach { b ->
                    calculatedSignature.append(String.format("%02X", b))
                }
                if (calculatedSignature.toString().equals(signature, true)) {
                    log.debug("Signature is valid")
                } else {
                    throw InvalidSignatureException("Signature is invalid")
                }
            }
        } ?: log.debug("Signature validation is disabled (webhook secret is not configured)")
        openSearchService?.let {
            if (eventType == "create" && event == "create") {
                it.registerGiteaCreateRefEvent(objectMapper.readValue(payload, GiteaCreateRefEvent::class.java))
            } else if (eventType == "delete" && event == "delete") {
                it.registerGiteaDeleteRefEvent(objectMapper.readValue(payload, GiteaDeleteRefEvent::class.java))
            } else if (eventType == "push" && event == "push") {
                it.registerGiteaPushEvent(objectMapper.readValue(payload, GiteaPushEvent::class.java))
            } else if (eventType == "pull_request" && event == "pull_request") {
                it.registerGiteaPullRequestEvent(objectMapper.readValue(payload, GiteaPullRequestEvent::class.java))
            }
        } ?: IndexerDisabledException("VCS indexation is disabled (opensearch integration is not configured)")
    }

    companion object {
        private const val MAC_ALGORITHM = "HmacSHA256"
        private val log = LoggerFactory.getLogger(GiteaIndexerController::class.java)
    }
}