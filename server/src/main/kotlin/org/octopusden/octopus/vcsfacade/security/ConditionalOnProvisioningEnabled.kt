package org.octopusden.octopus.vcsfacade.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Loads a bean only when repository provisioning is switched on (`vcs-facade.provisioning.enabled=true`).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(name = ["vcs-facade.provisioning.enabled"], havingValue = "true")
annotation class ConditionalOnProvisioningEnabled
