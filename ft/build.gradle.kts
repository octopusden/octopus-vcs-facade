import com.avast.gradle.dockercompose.ComposeExtension
import java.util.Base64
import org.octopusden.octopus.service.OcTemplateService

plugins {
    id("com.avast.gradle.docker-compose")
}

fun String.getExt() = project.ext[this] as String

val commonOkdParameters = mapOf(
    "DEPLOYMENT_PREFIX" to "vcs-facade-ft-$version".replace("[^-a-z0-9]".toRegex(), "-"),
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

fun String.getOkdPod() = "${commonOkdParameters["DEPLOYMENT_PREFIX"]}-$this"

fun String.getOkdHost() = "${getOkdPod()}-route-${"okdProject".getExt()}.${"okdClusterDomain".getExt()}:80"

fun String.getOkdExternalHost() = "${getOkdPod()}-service:${getPort()}"

val gitea = gradle.sharedServices.registerIfAbsent("giteaFtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/gitea.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("GITEA_IMAGE_TAG", properties["gitea.image-tag"] as String)
        workDirectory = layout.buildDirectory.dir("okd")
    }
}

val opensearch = gradle.sharedServices.registerIfAbsent("opensearchFtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/opensearch.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("OPENSEARCH_IMAGE_TAG", properties["opensearch.image-tag"] as String)
        workDirectory = layout.buildDirectory.dir("okd")
    }
}

val bitbucket = gradle.sharedServices.registerIfAbsent("bitbucketFtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/bitbucket.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("BITBUCKET_LICENSE", Base64.getEncoder().encodeToString("bitbucketLicense".getExt().toByteArray()))
        templateParameters.put("BITBUCKET_IMAGE_TAG", properties["bitbucket.image-tag"] as String)
        templateParameters.put("POSTGRES_IMAGE_TAG", properties["postgres.image-tag"] as String)
        workDirectory = layout.buildDirectory.dir("okd")
    }
}

val vcsFacade = gradle.sharedServices.registerIfAbsent("vcsFacadeFtService", OcTemplateService::class.java) {
    parameters.apply {
        namespace = "okdProject".getExt()
        templateFile = rootProject.layout.projectDirectory.file("okd/vcs-facade.yaml")
        templateParameters.putAll(commonOkdParameters)
        templateParameters.put("VCS_FACADE_IMAGE_TAG", version as String)
        templateParameters.put("VCS_FACADE_VCS_TYPE", "testProfile".getExt())
        templateParameters.put("VCS_FACADE_VCS_HOST", "testProfile".getExt().getOkdExternalHost())
        templateParameters.put("VCS_FACADE_OPENSEARCH_HOST", "opensearch".getOkdExternalHost())
        workDirectory = layout.buildDirectory.dir("okd")
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
            when ("testProfile".getExt()) {
                "gitea" -> {
                    usesService(gitea)
                    usesService(opensearch)
                    usesService(vcsFacade)
                    doFirst {
                        gitea.get().create()
                        opensearch.get().create()
                        gitea.get().waitPodsForReady()
                        opensearch.get().waitPodsForReady()
                        vcsFacade.get().create()
                        vcsFacade.get().waitPodsForReady()
                        "okdWebConsoleUrl".getExt().let {
                            if (it.isNotBlank()) {
                                logger.quiet("FT gitea pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"gitea".getOkdPod()}")
                                logger.quiet("FT opensearch pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"opensearch".getOkdPod()}")
                                logger.quiet("FT vcs-facade pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"vcs-facade".getOkdPod()}")
                            }
                        }
                    }
                    doLast {
                        gitea.get().logs("gitea".getOkdPod())
                        opensearch.get().logs("opensearch".getOkdPod())
                        vcsFacade.get().logs("vcs-facade".getOkdPod())
                        vcsFacade.get().delete()
                        gitea.get().delete()
                        opensearch.get().delete()
                    }
                }

                "bitbucket" -> {
                    usesService(bitbucket)
                    usesService(vcsFacade)
                    doFirst {
                        bitbucket.get().create()
                        bitbucket.get().waitPodsForReady()
                        vcsFacade.get().create()
                        vcsFacade.get().waitPodsForReady()
                        "okdWebConsoleUrl".getExt().let {
                            if (it.isNotBlank()) {
                                logger.quiet("FT bitbucket-db pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"bitbucket-db".getOkdPod()}")
                                logger.quiet("FT bitbucket pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"bitbucket".getOkdPod()}")
                                logger.quiet("FT vcs-facade pod: ${it}/k8s/ns/${"okdProject".getExt()}/pods/${"vcs-facade".getOkdPod()}")
                            }
                        }
                    }
                    doLast {
                        bitbucket.get().logs("bitbucket-db".getOkdPod())
                        bitbucket.get().logs("bitbucket".getOkdPod())
                        vcsFacade.get().logs("vcs-facade".getOkdPod())
                        vcsFacade.get().delete()
                        bitbucket.get().delete()
                    }
                }
            }
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
