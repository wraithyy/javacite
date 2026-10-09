plugins {
    base
}

group = "io.github.wraithyy"
// version comes from gradle.properties (override with -Pversion=...)

subprojects {
    group = rootProject.group
    version = rootProject.version
}

// javacite-maven is a standalone Maven build (correct plugin descriptor); it consumes javacite-core from mavenLocal.
val mavenBuild by tasks.registering(Exec::class) {
    dependsOn(":javacite-core:publishToMavenLocal")
    commandLine("mvn", "-B", "-q", "-f", "javacite-maven/pom.xml", "install")
}

tasks.named("build") { dependsOn(mavenBuild) }
