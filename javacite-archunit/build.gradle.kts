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
    testImplementation("org.springframework:spring-context:7.0.9")
    testImplementation("org.springframework:spring-web:7.0.9")
    testImplementation("org.springframework:spring-tx:7.0.9")
    testImplementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
    testImplementation("org.springframework.boot:spring-boot-test:3.5.16")
    testImplementation("org.mockito:mockito-core:5.24.0")
}

description = "ArchUnit rules for javacite"
