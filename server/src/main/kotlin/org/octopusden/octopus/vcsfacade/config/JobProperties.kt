package org.octopusden.octopus.vcsfacade.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("vcs-facade.job")
data class JobProperties(val fastWorkTimoutSecs: Int,val retryIntervalSecs: Int,  val executor: ExecutorProperties) {
    data class ExecutorProperties(val corePoolSize: Int, val maxPoolSize: Int, val queueCapacity: Int)
}
