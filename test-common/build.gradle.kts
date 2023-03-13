dependencies {
    implementation(project(":common"))
    implementation("org.junit.jupiter:junit-jupiter-engine:${project.properties["junit-jupiter.version"]}")
    implementation("org.junit.jupiter:junit-jupiter-params:${project.properties["junit-jupiter.version"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.octopusden.infrastructure:bitbucket-test-client:${project.properties["external-systems-client.version"]}")
}
