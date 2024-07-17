import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.io.FileInputStream

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
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(30))
    }
}

/**
 * Loads properties from a local.properties file and sets them as extra properties in the project.
 *
 * This script checks if the local.properties file exists in the project's root directory. If the file
 * exists, it loads the properties from the file and sets them as extra properties in the project.
 * These properties can then be accessed throughout the build script using the project property mechanism.
 */
subprojects {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val localProperties = Properties()
        localProperties.load(FileInputStream(localPropertiesFile))
        localProperties.forEach { key, value ->
            project.extensions.extraProperties.set(key.toString(), value)
        }
    }
}

/**
 * Sanitizes a Helm release name to ensure it adheres to Helm's naming conventions.
 *
 * Helm release names must:
 * - Be no longer than 53 characters.
 * - Contain only lowercase letters (`a-z`), digits (`0-9`), and hyphens (`-`).
 * - Start with a letter.
 * - End with a letter or digit.
 *
 * @param name The original release name to be sanitized.
 * @return A sanitized release name that conforms to Helm's naming conventions.
 */
fun sanitizeHelmReleaseName(name: String?): String? {
    if (name == null) {
        return null
    }

    var sanitized = name.lowercase().replace(Regex("[^a-z0-9-]"), "-")

    if (!sanitized.first().isLetter()) {
        sanitized = "a$sanitized"
    }

    if (!sanitized.last().isLetterOrDigit()) {
        sanitized = sanitized.dropLastWhile { !it.isLetterOrDigit() }
        if (sanitized.isEmpty()) {
            sanitized = "default-release"
        }
    }

    if (sanitized.length > 53) {
        sanitized = sanitized.take(53)
    }

    if (!sanitized.last().isLetterOrDigit()) {
        sanitized = sanitized.dropLastWhile { !it.isLetterOrDigit() }
    }

    return sanitized
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
            mavenBom("org.springframework.boot:spring-boot-dependencies:${project.properties["spring-boot.version"]}")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${project.properties["spring-cloud.version"]}")
        }
    }

    ext {
        System.getenv().let {
            set("signingRequired", it.containsKey("ORG_GRADLE_PROJECT_signingKey") && it.containsKey("ORG_GRADLE_PROJECT_signingPassword"))
            set("testProfile", it.getOrDefault("TEST_PROFILE", project.properties["test.profile"]))
            set("dockerRegistry", it.getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]))
            set("octopusGithubDockerRegistry", it.getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]))
            set("bitbucketLicense", it.getOrDefault("BITBUCKET_LICENSE", project.properties["bitbucket.license"]))

            val platform = it.getOrDefault("PLATFORM", project.properties["platform"])
            set("platform", platform)
            val helmRelease = sanitizeHelmReleaseName(it.getOrDefault("HELM_RELEASE", project.properties["helmRelease"]) as String?)
            set("helmRelease", helmRelease)
            val helmNamespace = it.getOrDefault("HELM_NAMESPACE", project.properties["helmNamespace"])
            set("helmNamespace", helmNamespace)
            val clusterDomain = it.getOrDefault("CLUSTER_DOMAIN", project.properties["clusterDomain"])
            set( "clusterDomain", clusterDomain)
            set("localDomain", it.getOrDefault("LOCAL_DOMAIN", project.properties["localDomain"]))
            set("bitbucketHost", "$helmRelease-bitbucket-route-$helmNamespace.$clusterDomain")
            set("giteaHost", "$helmRelease-gitea-route-$helmNamespace.$clusterDomain")
        }
    }

    val supportedTestProfiles = listOf("bitbucket", "gitea", "gitlab")
    if (project.ext["testProfile"] !in supportedTestProfiles) {
        throw IllegalArgumentException("Test profile must be set to one of the following $supportedTestProfiles. Start gradle build with -Ptest.profile=... or set env variable TEST_PROFILE")
    }
    val mandatoryProperties = listOf("dockerRegistry", "octopusGithubDockerRegistry").plus(
        if (project.ext["testProfile"] == "bitbucket") listOf("bitbucketLicense") else emptyList()
    )
    val emptyProperties = mandatoryProperties.filter { (project.ext[it] as? String).isNullOrBlank() }
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
}
