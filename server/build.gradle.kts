buildscript {
    dependencies {
        classpath("com.bmuschko:gradle-docker-plugin:3.6.2")
    }
}

plugins {
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlin.plugin.noarg")
    id("com.bmuschko.docker-spring-boot-application") version "6.4.0"
    `maven-publish`
    id("com.avast.gradle.docker-compose") version "0.16.9"
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
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["bootJar"])
}

val dockerRegistry = System.getenv().getOrDefault("DOCKER_REGISTRY", project.properties["docker.registry"]) as? String
val octopusGithubDockerRegistry = System.getenv().getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]) as? String
val bitbucketLicense = System.getenv().getOrDefault("BITBUCKET_LICENSE", project.properties["bitbucket.license"]) as? String

configure<com.avast.gradle.dockercompose.ComposeExtension> {
    useComposeFiles.add("${projectDir}/docker/docker-compose.yml")
    waitForTcpPorts.set(true)
    captureContainersOutputToFiles.set(buildDir.resolve("docker_logs"))
    environment.putAll(
        mapOf(
            "DOCKER_REGISTRY" to dockerRegistry,
            "BITBUCKET_LICENSE" to bitbucketLicense
        )
    )
}

tasks.getByName("composeUp").doFirst {
    if (dockerRegistry.isNullOrBlank() || bitbucketLicense.isNullOrBlank()) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (dockerRegistry.isNullOrBlank()) " -Pdocker.registry=..." else "") +
                    (if (bitbucketLicense.isNullOrBlank()) " -Pbitbucket.license=..." else "") +
                    " or set env variable(s):" +
                    (if (dockerRegistry.isNullOrBlank()) " DOCKER_REGISTRY" else "") +
                    (if (bitbucketLicense.isNullOrBlank()) " BITBUCKET_LICENSE" else "")
        )
    }
}

docker {
    springBootApplication {
        baseImage.set("$dockerRegistry/openjdk:11")
        ports.set(listOf(8080, 8080))
        images.set(setOf("$octopusGithubDockerRegistry/octopusden/${project.name}:${project.version}"))
    }
}

tasks.getByName("dockerBuildImage").doFirst {
    if (dockerRegistry.isNullOrBlank() || octopusGithubDockerRegistry.isNullOrBlank()) {
        throw IllegalArgumentException(
            "Start gradle build with" +
                    (if (dockerRegistry.isNullOrBlank()) " -Pdocker.registry=..." else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " -Poctopus.github.docker.registry=..." else "") +
                    " or set env variable(s):" +
                    (if (dockerRegistry.isNullOrBlank()) " DOCKER_REGISTRY" else "") +
                    (if (octopusGithubDockerRegistry.isNullOrBlank()) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "")
        )
    }
}


sourceSets {
    test {
        resources {
            srcDir(project.rootDir.toString() + File.separator + "test-data")
        }
    }
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

    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${project.properties["spring-cloud.version"]}"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${project.properties["spring-boot.version"]}"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client") {
        exclude(group = "javax.ws.rs") //use jakarta.ws.rs from gitlab4j-api
    }
    implementation("io.micrometer:micrometer-registry-prometheus:1.9.5")

    implementation("org.gitlab4j:gitlab4j-api:5.3.0")

    implementation("org.springdoc:springdoc-openapi-ui:1.6.7")

    implementation("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-client:${project.properties["external-systems-client.version"]}") {
        exclude(group = "org.slf4j")
    }

    testImplementation("org.jetbrains.spek:spek-api:1.1.5")
    testRuntimeOnly("org.jetbrains.spek:spek-junit-platform-engine:1.1.5")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-common"))
}
