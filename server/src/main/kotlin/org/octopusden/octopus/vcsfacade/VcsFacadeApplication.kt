package org.octopusden.octopus.vcsfacade

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication(exclude = [ElasticsearchDataAutoConfiguration::class])
@ConfigurationPropertiesScan
class VcsFacadeApplication

fun main(args: Array<String>) {
    SpringApplication.run(VcsFacadeApplication::class.java, *args)
}
