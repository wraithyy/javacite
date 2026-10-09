plugins {
    id("javacite.java-conventions")
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
}

val functionalTest = sourceSets.create("functionalTest")

dependencies {
    implementation(project(":javacite-core"))
    // Applied programmatically by later wiring tasks via pluginManager.apply(...)
    implementation(libs.spotless.plugin)
    implementation(libs.errorprone.plugin)
    implementation(libs.spotbugs.plugin)
    implementation(libs.dependency.check.plugin)
    implementation(libs.sonar.plugin)

    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(platform(libs.junit.bom))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(libs.assertj)
    "functionalTestRuntimeOnly"(libs.junit.launcher)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

gradlePlugin {
    website = "https://github.com/wraithyy/javacite"
    vcsUrl = "https://github.com/wraithyy/javacite.git"
    plugins {
        create("javacite") {
            id = "io.github.wraithyy.javacite"
            implementationClass = "io.github.wraithyy.javacite.gradle.JavacitePlugin"
            displayName = "javacite"
            description = "Strict, opt-out Java guardrails for agent-driven development from one javacite.yml"
            tags = listOf("java", "quality", "static-analysis", "errorprone", "spotbugs", "ai")
        }
    }
}

gradlePlugin {
    testSourceSets(functionalTest)
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs Gradle TestKit functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(functionalTestTask)
}

// Single source of truth for pinned tool versions: libs.versions.toml -> JavaciteVersions constants.
val generateJavaciteVersions = tasks.register("generateJavaciteVersions") {
    val outDir = layout.buildDirectory.dir("generated/sources/versions")
    val versions = linkedMapOf(
        "PALANTIR_JAVA_FORMAT" to libs.versions.palantir.java.format.get(),
        "ERRORPRONE_CORE" to libs.versions.errorprone.core.get(),
        "NULLAWAY" to libs.versions.nullaway.get(),
        "JSPECIFY" to libs.versions.jspecify.get(),
        "CHECKSTYLE" to libs.versions.checkstyle.asProvider().get(),
        "PMD" to libs.versions.pmd.asProvider().get(),
        "SPOTBUGS" to libs.versions.spotbugs.asProvider().get(),
        "JACOCO" to libs.versions.jacoco.get(),
        "COMMONS_LANG3" to libs.versions.commons.lang3.get(),
    )
    inputs.property("versions", versions)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("io/github/wraithyy/javacite/gradle/JavaciteVersions.java").asFile
        file.parentFile.mkdirs()
        val constants = versions.entries.joinToString("\n") { (k, v) -> "    public static final String $k = \"$v\";" }
        file.writeText(
            "package io.github.wraithyy.javacite.gradle;\n\n" +
                "/** Generated from gradle/libs.versions.toml; do not edit. */\n" +
                "public final class JavaciteVersions {\n$constants\n\n    private JavaciteVersions() {}\n}\n",
        )
    }
}

sourceSets.main {
    java.srcDir(generateJavaciteVersions)
}

description = "Gradle plugin that wires javacite quality gates from one javacite.yml"
