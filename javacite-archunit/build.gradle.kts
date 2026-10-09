plugins {
    `java-library`
    id("javacite.java-conventions")
    id("javacite.publishing")
}

dependencies {
    api(libs.archunit.junit5)
    implementation(project(":javacite-core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
    // Fixture-only: the library itself matches Spring annotations by name.
    testImplementation("org.springframework:spring-context:6.2.19")
    testImplementation("org.springframework:spring-web:6.2.19")
    testImplementation("org.springframework:spring-tx:6.2.19")
    testImplementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
    testImplementation("org.springframework.boot:spring-boot-test:4.1.1")
    testImplementation("org.mockito:mockito-core:5.24.0")
}

description = "ArchUnit rules for javacite"
