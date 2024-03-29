import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    idea
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("io.github.gradle-nexus.publish-plugin")
    signing
}

allprojects {
    group = "org.octopusden.octopus.vcsfacade"
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "idea")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "signing")

    repositories {
        mavenCentral()
    }

    idea.module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }

    java {
        withJavadocJar()
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            suppressWarnings = true
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging{
            info.events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        }
        if ((project.properties["gitlab.skip"] as String).toBoolean()) {
            exclude("**/*Gitlab*")
        }
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${project.properties["spring-boot.version"]}")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${project.properties["spring-cloud.version"]}")
        }
    }

    ext {
        System.getenv().let {
            set("signingRequired", it.containsKey("ORG_GRADLE_PROJECT_signingKey") && it.containsKey("ORG_GRADLE_PROJECT_signingPassword"))
            set("dockerRegistry", it.getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]))
            set("octopusGithubDockerRegistry", it.getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]))
            set("bitbucketLicense", it.getOrDefault("BITBUCKET_LICENSE", project.properties["bitbucket.license"]))
        }
        set("validateFun", { properties: List<String> ->
            val emptyProperties = properties.filter { (project.ext[it] as? String).isNullOrBlank() }
            if (emptyProperties.isNotEmpty()) {
                throw IllegalArgumentException(
                    "Start gradle build with" +
                            (if (emptyProperties.contains("dockerRegistry")) " -Pdocker.registry=..." else "") +
                            (if (emptyProperties.contains("octopusGithubDockerRegistry")) " -Poctopus.github.docker.registry=..." else "") +
                            (if (emptyProperties.contains("bitbucketLicense")) " -Pbitbucket.license=..." else "") +
                            " or set env variable(s):" +
                            (if (emptyProperties.contains("dockerRegistry")) " DOCKER_REGISTRY" else "") +
                            (if (emptyProperties.contains("octopusGithubDockerRegistry")) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                            (if (emptyProperties.contains("bitbucketLicense")) " BITBUCKET_LICENSE" else "")
                )
            }
        })
    }
}
