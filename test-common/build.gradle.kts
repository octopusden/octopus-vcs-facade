dependencies {
    implementation(project(":common"))
    implementation("org.junit.jupiter:junit-jupiter-engine:${project.properties["junit-jupiter.version"]}")
    implementation("org.junit.jupiter:junit-jupiter-params:${project.properties["junit-jupiter.version"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    api("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-test-client:${project.properties["external-systems-client.version"]}")
    api("org.octopusden.octopus.octopus-external-systems-clients:gitlab-test-client:${project.properties["external-systems-client.version"]}")
    api("org.octopusden.octopus.octopus-external-systems-clients:gitea-test-client:${project.properties["external-systems-client.version"]}")
}
