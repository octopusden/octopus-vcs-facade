import com.avast.gradle.dockercompose.ComposeExtension
import java.util.Base64

plugins {
    id("com.avast.gradle.docker-compose")
    id("org.octopusden.octopus.oc-template")
}

fun String.getExt() = project.ext[this] as String

val commonOkdParameters = mapOf(
    "ACTIVE_DEADLINE_SECONDS" to "okdActiveDeadlineSeconds".getExt(),
    "DOCKER_REGISTRY" to "dockerRegistry".getExt()
)

fun String.getPort() = when (this) {
    "bitbucket" -> 7990
    "gitea" -> 3000
    "opensearch" -> 9200
    "vcs-facade" -> 8080
    else -> throw Exception("Unknown service '$this'")
}

fun String.getDockerHost() = "localhost:${getPort()}"

fun String.getDockerExternalHost() = "$this:${getPort()}"

fun String.getOkdPod(): String = ocTemplate.getPod(this)

fun String.getOkdHost(): String = ocTemplate.getOkdHost(this)

fun String.getOkdExternalHost() = "${getOkdPod()}-service:${getPort()}"

ocTemplate {
    namespace.set("okdProject".getExt())
    workDir.set(layout.buildDirectory.dir("okd"))

    clusterDomain.set("okdClusterDomain".getExt())
    prefix.set("vcs-facade-ft")

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
            parameters.set(commonOkdParameters + mapOf(
                "BITBUCKET_LICENSE" to Base64.getEncoder().encodeToString("bitbucketLicense".getExt().toByteArray()),
                "BITBUCKET_IMAGE_TAG" to properties["bitbucket.image-tag"] as String,
                "POSTGRES_IMAGE_TAG" to properties["postgres.image-tag"] as String
            ))
        }
    }

    service("vcs-facade") {
        templateFile.set(rootProject.layout.projectDirectory.file("okd/vcs-facade.yaml"))
        parameters.set(commonOkdParameters + mapOf(
            "VCS_FACADE_IMAGE_TAG" to version as String,
            "VCS_FACADE_VCS_TYPE" to "testProfile".getExt(),
            "VCS_FACADE_VCS_HOST" to "testProfile".getExt().getOkdExternalHost(),
            "VCS_FACADE_OPENSEARCH_HOST" to "opensearch".getOkdExternalHost()
        ))
        if ("testProfile".getExt() == "gitea") {
            dependsOn.set(listOf("gitea", "opensearch"))
        } else {
            dependsOn.set(listOf("bitbucket"))
        }
    }
}

configure<ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "OCTOPUS_GITHUB_DOCKER_REGISTRY" to "octopusGithubDockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "BITBUCKET_IMAGE_TAG" to properties["bitbucket.image-tag"],
            "POSTGRES_IMAGE_TAG" to properties["postgres.image-tag"],
            "GITEA_IMAGE_TAG" to properties["gitea.image-tag"],
            "OPENSEARCH_IMAGE_TAG" to properties["opensearch.image-tag"],
            "VCS_FACADE_IMAGE_TAG" to version
        )
    )
}

tasks["composeUp"].apply{
    dependsOn(":vcs-facade:dockerBuildImage")
    doLast {
        if ("testProfile".getExt() == "gitea") {
            exec {
                setCommandLine("docker", "exec", "vcs-facade-ft-gitea", "/script/add_admin.sh")
            }.assertNormalExitValue()
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

ftImplementation.isCanBeResolved = true

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
            dependsOn(":vcs-facade:dockerPushImage")
            systemProperties["test.vcs-host"] = "testProfile".getExt().getOkdHost()
            systemProperties["test.vcs-external-host"] = "testProfile".getExt().getOkdExternalHost()
            systemProperties["test.vcs-facade-host"] = "vcs-facade".getOkdHost()
            systemProperties["test.vcs-facade-external-host"] = "vcs-facade".getOkdExternalHost()
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
    scopes["PROVIDED"]?.get("plus")?.add(configurations["ftImplementation"])
}

dependencies {
    ftImplementation(project(":client"))
    ftImplementation(project(":common"))
    ftImplementation(project(":test-common"))
    ftImplementation("org.junit.jupiter:junit-jupiter-engine")
    ftImplementation("org.junit.jupiter:junit-jupiter-params")
}
