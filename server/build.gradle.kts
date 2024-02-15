plugins {
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("com.avast.gradle.docker-compose")
    id("com.bmuschko.docker-spring-boot-application")
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

@Suppress("UNCHECKED_CAST")
val extValidateFun = project.ext["validateFun"] as ((List<String>) -> Unit)
fun String.getExt() = project.ext[this] as? String

configure<com.avast.gradle.dockercompose.ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(layout.buildDirectory.file("docker_logs").get().asFile)
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to "dockerRegistry".getExt(),
            "BITBUCKET_LICENSE" to "bitbucketLicense".getExt()
        )
    )
}

tasks.getByName("composeUp").doFirst {
    extValidateFun.invoke(listOf("dockerRegistry", "bitbucketLicense"))
}

tasks["composeUp"].doLast {
    logger.info("Create test-admin in Gitea")
    val process = ProcessBuilder(
        "docker", "exec", "vcs-facade-ut-gitea",
        "/tmp/add_admin.sh"
    ).start()
    process.waitFor(10, TimeUnit.SECONDS)

    val output = process.inputStream.bufferedReader().readText()
    logger.info(output)

    val error = process.errorStream.bufferedReader().readText()
    if (error.isNotEmpty()) {
        throw GradleException(error)
    }
}

docker {
    springBootApplication {
        baseImage.set("${"dockerRegistry".getExt()}/eclipse-temurin:17-jdk")
        ports.set(listOf(8080, 8080))
        images.set(setOf("${"octopusGithubDockerRegistry".getExt()}/octopusden/${project.name}:${project.version}"))
    }
}

tasks.getByName("dockerBuildImage").doFirst {
    extValidateFun.invoke(listOf("dockerRegistry", "octopusGithubDockerRegistry"))
}

tasks.withType<Test> {
    dependsOn("composeUp")
}

dockerCompose.isRequiredBy(tasks["test"])

springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springdoc-openapi.version"]}")
    implementation("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:${project.properties["external-systems-client.version"]}") {
        exclude("commons-logging", "commons-logging")
    }
    implementation("org.octopusden.octopus.octopus-external-systems-clients:gitea-client:${project.properties["external-systems-client.version"]}") {
        exclude("commons-logging", "commons-logging")
    }
    implementation("org.gitlab4j:gitlab4j-api:${project.properties["gitlab4j-api.version"]}")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-common"))
}
