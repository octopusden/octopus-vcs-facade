import java.net.InetAddress
import java.time.Duration
import java.util.zip.CRC32
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

val defaultVersion = "${
    with(CRC32()) {
        update(InetAddress.getLocalHost().hostName.toByteArray())
        value
    }
}-snapshot"

allprojects {
    group = "org.octopusden.octopus.vcsfacade"
    if (version == "unspecified") {
        version = defaultVersion
    }
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
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(30))
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            suppressWarnings = true
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            info.events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        }
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${properties["spring-boot.version"]}")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${properties["spring-cloud.version"]}")
        }
    }

    ext {
        System.getenv().let {
            set(
                "signingRequired",
                it.containsKey("ORG_GRADLE_PROJECT_signingKey") && it.containsKey("ORG_GRADLE_PROJECT_signingPassword")
            )
            set("testPlatform", it.getOrDefault("TEST_PLATFORM", properties["test.platform"]))
            set("testProfile", it.getOrDefault("TEST_PROFILE", properties["test.profile"]))
            set("dockerRegistry", it.getOrDefault("DOCKER_REGISTRY", properties["docker.registry"]))
            set(
                "octopusGithubDockerRegistry",
                it.getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"])
            )
            set("okdProject", it.getOrDefault("OKD_PROJECT", properties["okd.project"]))
            set("okdClusterDomain", it.getOrDefault("OKD_CLUSTER_DOMAIN", properties["okd.cluster-domain"]))
            set(
                "okdWebConsoleUrl",
                (it.getOrDefault("OKD_WEB_CONSOLE_URL", properties["okd.web-console-url"]) as? String)?.trimEnd('/')
            )
            set("bitbucketLicense", it.getOrDefault("BITBUCKET_LICENSE", properties["bitbucket.license"]))
        }
    }

    val supportedTestPlatforms = listOf("docker", "okd")
    if (project.ext["testPlatform"] !in supportedTestPlatforms) {
        throw IllegalArgumentException("Test platform must be set to one of the following $supportedTestPlatforms. Start gradle build with -Ptest.platform=... or set env variable TEST_PLATFORM")
    }
    val supportedTestProfiles = listOf("bitbucket", "gitea")
    if (project.ext["testProfile"] !in supportedTestProfiles) {
        throw IllegalArgumentException("Test profile must be set to one of the following $supportedTestProfiles. Start gradle build with -Ptest.profile=... or set env variable TEST_PROFILE")
    }
    val mandatoryProperties = mutableListOf("dockerRegistry", "octopusGithubDockerRegistry")
    if (project.ext["testPlatform"] == "okd") {
        mandatoryProperties.add("okdProject")
        mandatoryProperties.add("okdClusterDomain")
    }
    if (project.ext["testProfile"] == "bitbucket") {
        mandatoryProperties.add("bitbucketLicense")
    }
    val undefinedProperties = mandatoryProperties.filter { (project.ext[it] as String).isBlank() }
    if (undefinedProperties.isNotEmpty()) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (undefinedProperties.contains("dockerRegistry")) " -Pdocker.registry=..." else "") +
                    (if (undefinedProperties.contains("octopusGithubDockerRegistry")) " -Poctopus.github.docker.registry=..." else "") +
                    (if (undefinedProperties.contains("okdProject")) " -Pokd.project=..." else "") +
                    (if (undefinedProperties.contains("okdClusterDomain")) " -Pokd.cluster-domain=..." else "") +
                    (if (undefinedProperties.contains("bitbucketLicense")) " -Pbitbucket.license=..." else "") +
                    " or set env variable(s):" +
                    (if (undefinedProperties.contains("dockerRegistry")) " DOCKER_REGISTRY" else "") +
                    (if (undefinedProperties.contains("octopusGithubDockerRegistry")) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                    (if (undefinedProperties.contains("okdProject")) " OKD_PROJECT" else "") +
                    (if (undefinedProperties.contains("okdClusterDomain")) " OKD_CLUSTER_DOMAIN" else "") +
                    (if (undefinedProperties.contains("bitbucketLicense")) " BITBUCKET_LICENSE" else "")
        )
    }
}
