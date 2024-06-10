dependencies {
    implementation(project(":common"))
    implementation("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.junit.jupiter:junit-jupiter-params")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.yaml:snakeyaml:1.29")
    api("org.octopusden.octopus.octopus-external-systems-clients:bitbucket-test-client:${project.properties["external-systems-client.version"]}")
    api("org.octopusden.octopus.octopus-external-systems-clients:gitea-test-client:${project.properties["external-systems-client.version"]}")
    api("org.octopusden.octopus.octopus-external-systems-clients:gitlab-test-client:${project.properties["external-systems-client.version"]}")
    constraints {
        api("org.gitlab4j:gitlab4j-api:${project.properties["gitlab4j-api.version"]}")
    }
}
