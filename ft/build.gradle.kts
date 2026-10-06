import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.process.ExecOperations
import java.util.Base64
import javax.inject.Inject

plugins {
    id("com.avast.gradle.docker-compose")
    id("org.octopusden.octopus.oc-template")
}

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
        "vcs-facade" -> 8080
        else -> throw Exception("Unknown service '$this'")
    }

fun String.getDockerHost() = "localhost:${getPort()}"

fun String.getDockerExternalHost() = "$this:${getPort()}"

fun getOkdExternalHost(serviceName: String) = "${ocTemplate.getPod(serviceName)}-service:${serviceName.getPort()}"

ocTemplate {
    workDir.set(layout.buildDirectory.dir("okd"))

    clusterDomain.set("okdClusterDomain".getExt())
    namespace.set("okdProject".getExt())
    prefix.set("vcs-facade-ft")
    attempts.set(25)

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

    service("vcs-facade") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/vcs-facade.yaml"))
        parameters.set(
            commonOkdParameters + mapOf(
                "VCS_FACADE_IMAGE_TAG" to version as String,
                "VCS_FACADE_VCS_TYPE" to "testProfile".getExt(),
                "VCS_FACADE_VCS_HOST" to getOkdExternalHost("testProfile".getExt()),
                "VCS_FACADE_OPENSEARCH_HOST" to getOkdExternalHost("opensearch"),
                "APPLICATION_DEV_CONTENT" to layout.projectDirectory
                    .dir("docker/application-vcs-facade.yml")
                    .asFile
                    .readText(),
                "APPLICATION_GITEA_CONTENT" to layout.projectDirectory
                    .dir("docker/gitea/application-gitea.yml")
                    .asFile
                    .readText(),
                "APPLICATION_BITBUCKET_CONTENT" to layout.projectDirectory
                    .dir("docker/bitbucket/application-bitbucket.yml")
                    .asFile
                    .readText(),
            ),
        )
        if ("testProfile".getExt() == "gitea") {
            dependsOn.set(listOf("gitea", "opensearch"))
        } else {
            dependsOn.set(listOf("bitbucket"))
        }
    }
}

tasks["ocProcess"].dependsOn(":vcs-facade:dockerPushImage")

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
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "BITBUCKET_IMAGE_TAG" to providers.gradleProperty("bitbucket.image-tag").get(),
            "POSTGRES_IMAGE_TAG" to providers.gradleProperty("postgres.image-tag").get(),
            "GITEA_IMAGE_TAG" to providers.gradleProperty("gitea.image-tag").get(),
            "OPENSEARCH_IMAGE_TAG" to providers.gradleProperty("opensearch.image-tag").get(),
            "VCS_FACADE_IMAGE_TAG" to version as String,
        ),
    )
}

// Gradle 9 removed Project.exec; a build script reaches process execution through ExecOperations.
interface ExecOperationsHolder {
    @get:Inject
    val execOperations: ExecOperations
}

val execOperations = objects.newInstance<ExecOperationsHolder>().execOperations

tasks["composeUp"].apply {
    dependsOn(":vcs-facade:dockerBuildImage")
    doLast {
        if ("testProfile".getExt() == "gitea") {
            // exec fails on a non-zero exit value by default.
            execOperations.exec {
                setCommandLine("docker", "exec", "vcs-facade-ft-gitea", "/script/add_admin.sh")
            }
        }
    }
}

sourceSets {
    create("ft") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val ftImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

configurations["ftRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val ft by tasks.creating(Test::class) {
    mustRunAfter(":vcs-facade:test")
    group = "verification"
    description = "Runs the integration tests"
    testClassesDirs = sourceSets["ft"].output.classesDirs
    classpath = sourceSets["ft"].runtimeClasspath
    systemProperties["test.profile"] = "testProfile".getExt()
    when ("testPlatform".getExt()) {
        "okd" -> {
            systemProperties["test.vcs-host"] = ocTemplate.getOkdHost("testProfile".getExt())
            systemProperties["test.vcs-external-host"] = getOkdExternalHost("testProfile".getExt())
            systemProperties["test.vcs-facade-host"] = ocTemplate.getOkdHost("vcs-facade")
            systemProperties["test.vcs-facade-external-host"] = getOkdExternalHost("vcs-facade")
            ocTemplate.isRequiredBy(this)
        }
        "docker" -> {
            systemProperties["test.vcs-host"] = "testProfile".getExt().getDockerHost()
            systemProperties["test.vcs-external-host"] = "testProfile".getExt().getDockerExternalHost()
            systemProperties["test.vcs-facade-host"] = "vcs-facade".getDockerHost()
            systemProperties["test.vcs-facade-external-host"] = "vcs-facade".getDockerExternalHost()
            dockerCompose.isRequiredBy(this)
        }
    }
}

idea.module {
    // The resolvable view of ftImplementation: a declare-only configuration cannot be resolved.
    scopes["PROVIDED"]?.get("plus")?.add(configurations["ftCompileClasspath"])
}

dependencies {
    ftImplementation(project(":client"))
    ftImplementation(project(":common"))
    ftImplementation(project(":test-common"))
    ftImplementation("org.junit.jupiter:junit-jupiter-engine")
    ftImplementation("org.junit.jupiter:junit-jupiter-params")
    // Gradle no longer puts the JUnit Platform launcher on the test runtime classpath itself.
    "ftRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
