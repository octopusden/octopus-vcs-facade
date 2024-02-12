pluginManagement {
    plugins {
        id("org.springframework.boot") version (extra["spring-boot.version"] as String)
        id("io.spring.dependency-management") version "1.1.4"
        val kotlinVersion = extra["kotlin.version"] as String
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        id("com.avast.gradle.docker-compose") version (extra["docker-compose-plugin.version"] as String)
        id("com.bmuschko.docker-spring-boot-application") version (extra["bmuschko-docker-plugin.version"] as String)
        id("io.github.gradle-nexus.publish-plugin") version "1.1.0" apply false
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "octopus-vcs-facade"

include(":common")
include(":client")
include(":test-common")
include(":server")
findProject(":server")?.name = "vcs-facade"
include(":ft")
