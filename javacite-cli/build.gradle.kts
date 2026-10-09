plugins {
    id("javacite.java-conventions")
    id("javacite.publishing")
    application
}

application {
    mainClass = "io.github.wraithyy.javacite.cli.Main"
}

dependencies {
    implementation(project(":javacite-core"))
    implementation(libs.picocli)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

// Plain fat jar so `jbang <GAV>` runs it without resolving dependencies: published artifact is self-contained.
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.github.wraithyy.javacite.cli.Main",
            "Implementation-Version" to project.version,
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF.bak", "module-info.class")
}

description = "CLI to bootstrap and diagnose javacite"
