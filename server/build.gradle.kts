import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.process.ExecOperations
import java.util.Base64
import javax.inject.Inject

plugins {
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.avast.gradle.docker-compose")
    id("com.bmuschko.docker-spring-boot-application")
    id("org.octopusden.octopus.oc-template")
}

// The Boot plugin (spring-boot-plugin.version) imports its own BOM when it is applied, after the root
// build's import of the runtime BOM, and the later import wins. Import the runtime BOM again, last, so
// the service keeps running on spring-boot.version.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${providers.gradleProperty("spring-boot.version").get()}")
    }
}

// This module is a deployable service: its artifact is the docker image built from `bootJar`,
// not a Maven dependency. It therefore declares no Maven publication — see the
// `octopusQuality { publication { centralPublications } }` declaration in the root build script.

fun String.getExt() = project.ext[this] as String

val commonOkdParameters = mapOf(
    "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
    "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
)

fun String.getPort() =
    when (this) {
        "bitbucket" -> 7990
        "gitea" -> 3000
        "opensearch" -> 9200
        else -> throw Exception("Unknown service '$this'")
    }

fun String.getDockerHost() = "localhost:${getPort()}"

ocTemplate {
    workDir.set(layout.buildDirectory.dir("okd"))

    clusterDomain.set("okdClusterDomain".getExt())
    namespace.set("okdProject".getExt())
    prefix.set("vcs-facade-ut")

    "okdWebConsoleUrl".getExt().takeIf { it.isNotBlank() }?.let {
        webConsoleUrl.set(it)
    }

    group("giteaServices").apply {
        enabled.set("testProfile".getExt() == "gitea")
        service("gitea") {
            templateFile.set(rootProject.layout.projectDirectory.file("okd/gitea.yaml"))
            parameters.set(commonOkdParameters + mapOf("GITEA_IMAGE_TAG" to properties["gitea.image-tag"] as String))
        }
        service("opensearch") {
            templateFile.set(rootProject.layout.projectDirectory.file("okd/opensearch.yaml"))
            parameters.set(commonOkdParameters + mapOf("OPENSEARCH_IMAGE_TAG" to properties["opensearch.image-tag"] as String))
        }
    }

    group("bitbucketServices").apply {
        enabled.set("testProfile".getExt() == "bitbucket")
        service("bitbucket") {
            templateFile.set(rootProject.layout.projectDirectory.file("okd/bitbucket.yaml"))
            parameters.set(
                commonOkdParameters + mapOf(
                    "BITBUCKET_LICENSE" to Base64.getEncoder().encodeToString("bitbucketLicense".getExt().toByteArray()),
                    "BITBUCKET_IMAGE_TAG" to properties["bitbucket.image-tag"] as String,
                    "POSTGRES_IMAGE_TAG" to properties["postgres.image-tag"] as String,
                ),
            )
        }
    }
}

configure<ComposeExtension> {
    useComposeFiles.add("$projectDir/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    // The standalone docker-compose binary, as plugin 0.16 used; 0.17 defaults to `docker compose`.
    useDockerComposeV2.set(false)
    captureContainersOutputToFiles.set(
        layout.buildDirectory
            .file("docker_logs")
            .get()
            .asFile,
    )
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "BITBUCKET_IMAGE_TAG" to providers.gradleProperty("bitbucket.image-tag").get(),
            "POSTGRES_IMAGE_TAG" to providers.gradleProperty("postgres.image-tag").get(),
            "GITEA_IMAGE_TAG" to providers.gradleProperty("gitea.image-tag").get(),
            "OPENSEARCH_IMAGE_TAG" to providers.gradleProperty("opensearch.image-tag").get(),
        ),
    )
}

// Gradle 9 removed Project.exec; a build script reaches process execution through ExecOperations.
interface ExecOperationsHolder {
    @get:Inject
    val execOperations: ExecOperations
}

val execOperations = objects.newInstance<ExecOperationsHolder>().execOperations

// exec fails on a non-zero exit value by default.
tasks["composeUp"].doLast {
    if ("testProfile".getExt() == "gitea") {
        execOperations.exec {
            setCommandLine("docker", "exec", "vcs-facade-ut-gitea", "/script/add_admin.sh")
        }
    }
}

docker {
    springBootApplication {
        baseImage.set("${"dockerRegistry".getExt()}/eclipse-temurin:21-jdk")
        ports.set(listOf(8080, 8080))
        images.set(setOf("${"octopusGithubDockerRegistry".getExt()}/octopusden/$name:$version"))
    }
}

tasks.withType<Test> {
    when ("testPlatform".getExt()) {
        "okd" -> {
            systemProperties["test.opensearch-host"] = ocTemplate.getOkdHost("opensearch") + ":80"
            systemProperties["test.vcs-host"] = ocTemplate.getOkdHost("testProfile".getExt())
            ocTemplate.isRequiredBy(this)
        }
        "docker" -> {
            systemProperties["test.opensearch-host"] = "opensearch".getDockerHost()
            systemProperties["test.vcs-host"] = "testProfile".getExt().getDockerHost()
            dockerCompose.isRequiredBy(this)
        }
    }
    systemProperties["test.vcs-facade-host"] = "localhost:8080"
    systemProperties["spring.profiles.active"] = "ut,${"testProfile".getExt()}"
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.retry:spring-retry")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.opensearch.client:spring-data-opensearch:${properties["spring-data-opensearch.version"]}")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${properties["springdoc-openapi.version"]}")
    implementation(
        "org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:${properties["external-systems-client.version"]}",
    )
    implementation("org.octopusden.octopus.octopus-external-systems-clients:gitea-client:${properties["external-systems-client.version"]}")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-common"))
    // Gradle no longer puts the JUnit Platform launcher on the test runtime classpath itself.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.all {
    exclude("commons-logging", "commons-logging")
}
