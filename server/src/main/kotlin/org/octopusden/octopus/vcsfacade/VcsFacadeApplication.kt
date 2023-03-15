package org.octopusden.octopus.vcsfacade

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.cloud.netflix.eureka.EnableEurekaClient
import org.springframework.context.annotation.Configuration

@EnableEurekaClient
@SpringBootApplication
@Configuration
@ConfigurationPropertiesScan
class VcsFacadeApplication

fun main(args: Array<String>) {
    SpringApplication.run(VcsFacadeApplication::class.java, *args)
}
