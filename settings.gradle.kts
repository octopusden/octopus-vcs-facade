pluginManagement {
    plugins {
        val kotlinVersion = extra["kotlin.version"] as String
        id("org.springframework.boot") version (extra["spring-boot.version"] as String)
        id("org.jetbrains.kotlin.jvm") version (kotlinVersion)
        id("org.jetbrains.kotlin.plugin.spring") version (kotlinVersion)
        id("org.jetbrains.kotlin.plugin.jpa") version (kotlinVersion)
        id("org.jetbrains.kotlin.plugin.allopen") version (kotlinVersion)
        id("org.jetbrains.kotlin.plugin.noarg") version (kotlinVersion)
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "octopus-vcs-facade"

include(":server")
findProject(":server")?.name = "vcs-facade"

include(":common")
include(":client")
include(":ft")
include(":test-common")
