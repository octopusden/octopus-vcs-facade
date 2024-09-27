package org.octopusden.octopus.vcsfacade.controller

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.octopusden.octopus.vcsfacade.config.GiteaProperties
import org.octopusden.octopus.vcsfacade.dto.GiteaCreateRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaDeleteRefEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPullRequestEvent
import org.octopusden.octopus.vcsfacade.dto.GiteaPushEvent
import org.octopusden.octopus.vcsfacade.dto.IndexReport
import org.octopusden.octopus.vcsfacade.dto.VcsServiceType.GITEA
import org.octopusden.octopus.vcsfacade.exception.InvalidSignatureException
import org.octopusden.octopus.vcsfacade.service.GiteaIndexerService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("rest/api/1/indexer/gitea")
@ConditionalOnProperty(
    prefix = "vcs-facade",
    name = ["vcs.gitea.enabled", "opensearch.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class GiteaIndexerController(
    giteaProperties: GiteaProperties,
    private val giteaIndexerService: GiteaIndexerService,
    private val objectMapper: ObjectMapper
) {
    private val mac = giteaProperties.index?.webhookSecret?.let {
        Mac.getInstance(MAC_ALGORITHM).apply {
            init(SecretKeySpec(it.toByteArray(), MAC_ALGORITHM))
        }
    }
        get() = field?.clone() as Mac?

    @PostMapping("webhook")
    fun processWebhookEvent(
        @RequestHeader("x-gitea-event") event: String,
        @RequestHeader("x-gitea-event-type") eventType: String,
        @RequestHeader("x-gitea-signature") signature: String?,
        request: HttpServletRequest
    ) {
        log.debug("Receive webhook event {}:{}", event, eventType)
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
                    log.trace("Signature is valid")
                } else {
                    throw InvalidSignatureException("Signature is invalid")
                }
            }
        } ?: log.trace("Signature validation is disabled (webhook secret is not configured)")
        if (event == "create" && eventType == "create") {
            with(objectMapper.readValue(payload, GiteaCreateRefEvent::class.java)) {
                log.info(
                    "Register '{}' {} creation in {} {} repository",
                    ref,
                    refType.jsonValue,
                    repository.fullName.lowercase(),
                    GITEA
                )
                giteaIndexerService.registerGiteaCreateRefEvent(this)
            }
        } else if (event == "delete" && eventType == "delete") {
            with(objectMapper.readValue(payload, GiteaDeleteRefEvent::class.java)) {
                log.info(
                    "Register '{}' {} deletion in {} {} repository",
                    ref,
                    refType.jsonValue,
                    repository.fullName.lowercase(),
                    GITEA
                )
                giteaIndexerService.registerGiteaDeleteRefEvent(this)
            }
        } else if (event == "push" && eventType == "push") {
            with(objectMapper.readValue(payload, GiteaPushEvent::class.java)) {
                log.info(
                    "Register {} commit(s) in {} {} repository",
                    commits.size,
                    repository.fullName.lowercase(),
                    GITEA
                )
                giteaIndexerService.registerGiteaPushEvent(this)
            }
        } else if (
            (event == "pull_request" && (eventType == "pull_request" || eventType == "pull_request_assign" || eventType == "pull_request_review_request")) ||
            (event == "pull_request_approved" && eventType == "pull_request_review_approved") ||
            (event == "pull_request_rejected" && eventType == "pull_request_review_rejected")
        ) {
            with(objectMapper.readValue(payload, GiteaPullRequestEvent::class.java)) {
                log.info(
                    "Register '{}' action for pull request {} in {} {} repository",
                    action,
                    pullRequest.number,
                    repository.fullName.lowercase(),
                    GITEA
                )
                giteaIndexerService.registerGiteaPullRequestEvent(this)
            }
        }
    }

    @PostMapping("scan")
    fun scanRepository(@RequestParam("sshUrl") sshUrl: String) {
        log.info("Schedule scan of {}", sshUrl)
        giteaIndexerService.scheduleRepositoryScan(sshUrl)
    }

    @GetMapping("report")
    fun getIndexReport(): IndexReport {
        log.info("Get {} repositories index report", GITEA)
        return giteaIndexerService.getIndexReport()
    }

    companion object {
        private const val MAC_ALGORITHM = "HmacSHA256"
        private val log = LoggerFactory.getLogger(GiteaIndexerController::class.java)
    }
}