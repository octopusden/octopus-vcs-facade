import com.avast.gradle.dockercompose.ComposeExtension
import java.util.Base64

plugins {
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.avast.gradle.docker-compose")
    id("com.bmuschko.docker-spring-boot-application")
    id("org.octopusden.octopus.oc-template")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bootJar") {
            artifact(tasks.getByName("bootJar"))
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("Octopus module: ${project.name}")
                url.set("https://github.com/octopusden/octopus-vcs-facade.git")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/kzaporozhtsev/octopus-vcs-facade.git")
                    connection.set("scm:git://github.com/octopusden/octopus-vcs-facade.git")
                }
                developers {
                    developer {
                        id.set("octopus")
                        name.set("octopus")
                    }
                }
            }
        }
    }
}

signing {
    isRequired = project.ext["signingRequired"] as Boolean
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["bootJar"])
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
    else -> throw Exception("Unknown service '$this'")
}

fun String.getDockerHost() = "localhost:${getPort()}"

ocTemplate {
    namespace.set("okdProject".getExt())
    workDir.set(layout.buildDirectory.dir("okd"))

    clusterDomain.set("okdClusterDomain".getExt())
    prefix.set("vcs-facade-ut")

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
}

configure<ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/${"testProfile".getExt()}/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt(),
            "BITBUCKET_IMAGE_TAG" to properties["bitbucket.image-tag"],
            "POSTGRES_IMAGE_TAG" to properties["postgres.image-tag"],
            "GITEA_IMAGE_TAG" to properties["gitea.image-tag"],
            "OPENSEARCH_IMAGE_TAG" to properties["opensearch.image-tag"],
        )
    )
}

tasks["composeUp"].doLast {
    if ("testProfile".getExt() == "gitea") {
        exec {
            setCommandLine("docker", "exec", "vcs-facade-ut-gitea", "/script/add_admin.sh")
        }.assertNormalExitValue()
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
    implementation("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:${properties["external-systems-client.version"]}")
    implementation("org.octopusden.octopus.octopus-external-systems-clients:gitea-client:${properties["external-systems-client.version"]}")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-common"))
}

configurations.all {
    exclude("commons-logging", "commons-logging")
}
