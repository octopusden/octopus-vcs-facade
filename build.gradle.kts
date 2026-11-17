import java.net.InetAddress
import java.time.Duration
import java.util.zip.CRC32
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    idea
    id("io.spring.dependency-management")
    id("org.jetbrains.kotlin.jvm")
    id("io.github.gradle-nexus.publish-plugin")
    signing
    // Kotlin static-analysis tools — declared at root (apply false), applied per Kotlin subproject below.
    id("io.gitlab.arturbosch.detekt") apply (false)
    id("org.jlleitschuh.gradle.ktlint") apply (false)
    // Octopus quality-gates convention plugin — configures detekt/ktlint and wires qualityStatic.
    id("org.octopusden.octopus-quality")
    id("org.sonarqube")
}

octopusQuality {
    // Regression guard on what this repository publishes to Maven Central, provided by the
    // shared policy from octopus-base v2.7.0 — this repository used to hand-roll a path-based
    // version of the same check (see git history), which is why it is deleted in this same
    // commit: the plugin registers a task named `verifyCentralPublicationPolicy`, and two tasks
    // of one name fail configuration.
    //
    // Unlike a single-module repository, the project PATH here is a meaningful part of the
    // identity: :client and :common each declare their own unconditional `publishing {}` block,
    // so the guard's real job is catching a THIRD module (:server, :test-common, :ft) gaining a
    // publication it should not have — not distinguishing what :client/:common publish from one
    // another.
    publication {
        enforceCentralPublications.set(true)
        centralPublications.set(
            setOf(
                ":client|maven|org.octopusden.octopus.vcsfacade:client|[jar, jar:javadoc, jar:sources]",
                ":common|maven|org.octopusden.octopus.vcsfacade:common|[jar, jar:javadoc, jar:sources]",
            ),
        )
    }
    // Repo has no coverage tool / no unit-test coverage target — disable coverage verification.
    coverage {
        enabled.set(false)
    }
    // Enforce the gate: detekt/ktlint violations fail the build. Current debt is absorbed by
    // the committed detekt-baseline.xml / ktlint-baseline.xml files.
    kotlin {
        failOnViolation.set(true)
    }
    // Functional-test tasks are excluded from the quality gate.
    // :vcs-facade:test is an infra-bound integration test: docker-compose (test.platform=docker)
    // wires composeBuild/composeUp onto it via dockerCompose.isRequiredBy(test), which cannot run
    // on the GitHub runner (no docker-compose / no OKD). Coverage is disabled for this repo, so the
    // qualityCoverage aggregate must not pull this task (and its compose chain) into the graph.
    excludeTasks("ft", ":ft:ft", ":vcs-facade:test")
}

val defaultVersion = "${
    with(CRC32()) {
        update(InetAddress.getLocalHost().hostName.toByteArray())
        value
    }
}-SNAPSHOT"

allprojects {
    group = "org.octopusden.octopus.vcsfacade"
    if (version == "unspecified") {
        version = defaultVersion
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
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
    // Kotlin static analysis — must be applied per subproject so the convention plugin's
    // reactive configuration wires detekt/ktlintCheck tasks (avoids a hollow quality gate).
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // detekt 1.23.8 bundles kotlin-compiler-embeddable 2.0.21. The Spring
    // dependency-management BOM (imported below) constrains every configuration —
    // including detekt's — and would otherwise downgrade it to the project Kotlin
    // (1.9.22), which detekt rejects ("compiled with Kotlin 2.0.21 but running with
    // 1.9.22"). Pin only the detekt configuration back to detekt's bundled version.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.0.21")
            }
        }
    }
    // The same BOM would also pull the Kotlin Gradle plugin's own tool classpaths (compiler, compiler
    // plugins, build tools) down to the runtime Kotlin, and the 2.x plugin cannot run a 1.9 compiler.
    // Let them resolve the versions the plugin requests.
    configurations.matching { it.name.startsWith("kotlin") && it.name.contains("Classpath") }.configureEach {
        resolutionStrategy.eachDependency {
            requested.version?.takeIf { it.isNotEmpty() }?.let { useVersion(it) }
        }
    }

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
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            suppressWarnings.set(true)
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // The Kotlin Gradle plugin (kotlin-plugin.version) is newer than the Kotlin runtime these modules
    // ship with and run on (kotlin.version). Hold the compiler to the runtime's level so the bytecode
    // and metadata stay what consumers of the published modules can read, and no call can reach a
    // stdlib API newer than the stdlib on the classpath.
    extensions.configure<KotlinJvmProjectExtension> {
        // The version the plugin gives kotlin-stdlib / kotlin-reflect and its own constraints on them;
        // it defaults to the plugin's version, which would leak into the published POMs and modules.
        coreLibrariesVersion = providers.gradleProperty("kotlin.version").get()
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_1_9)
            apiVersion.set(KotlinVersion.KOTLIN_1_9)
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
            set("signingRequired", it.containsKey("ORG_GRADLE_PROJECT_signingKey") && it.containsKey("ORG_GRADLE_PROJECT_signingPassword"))
            set("testPlatform", it.getOrDefault("TEST_PLATFORM", properties["test.platform"]))
            set("testProfile", it.getOrDefault("TEST_PROFILE", properties["test.profile"]))
            set("dockerRegistry", it.getOrDefault("DOCKER_REGISTRY", properties["docker.registry"]))
            set("octopusGithubDockerRegistry", it.getOrDefault("OCTOPUS_GITHUB_DOCKER_REGISTRY", project.properties["octopus.github.docker.registry"]))
            set("okdActiveDeadlineSeconds", it.getOrDefault("OKD_ACTIVE_DEADLINE_SECONDS", properties["okd.active-deadline-seconds"]))
            set("okdProject", it.getOrDefault("OKD_PROJECT", properties["okd.project"]))
            set("okdClusterDomain", it.getOrDefault("OKD_CLUSTER_DOMAIN", properties["okd.cluster-domain"]))
            set("okdWebConsoleUrl", (it.getOrDefault("OKD_WEB_CONSOLE_URL", properties["okd.web-console-url"]) as String).trimEnd('/'))
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
        mandatoryProperties.add("okdActiveDeadlineSeconds")
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
                    (if (undefinedProperties.contains("okdActiveDeadlineSeconds")) " -Pokd.active-deadline-seconds=..." else "") +
                    (if (undefinedProperties.contains("okdProject")) " -Pokd.project=..." else "") +
                    (if (undefinedProperties.contains("okdClusterDomain")) " -Pokd.cluster-domain=..." else "") +
                    (if (undefinedProperties.contains("bitbucketLicense")) " -Pbitbucket.license=..." else "") +
                    " or set env variable(s):" +
                    (if (undefinedProperties.contains("dockerRegistry")) " DOCKER_REGISTRY" else "") +
                    (if (undefinedProperties.contains("octopusGithubDockerRegistry")) " OCTOPUS_GITHUB_DOCKER_REGISTRY" else "") +
                    (if (undefinedProperties.contains("okdActiveDeadlineSeconds")) " OKD_ACTIVE_DEADLINE_SECONDS" else "") +
                    (if (undefinedProperties.contains("okdProject")) " OKD_PROJECT" else "") +
                    (if (undefinedProperties.contains("okdClusterDomain")) " OKD_CLUSTER_DOMAIN" else "") +
                    (if (undefinedProperties.contains("bitbucketLicense")) " BITBUCKET_LICENSE" else "")
        )
    }
}
