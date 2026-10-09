plugins {
    id("javacite.java-conventions")
    id("javacite.publishing")
}

dependencies {
    implementation(libs.snakeyaml.engine)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

// Task 1.3 validation: generated configs are loaded by the real tools in tests only.
val errorproneValidation = configurations.create("errorproneValidation") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    testImplementation(libs.checkstyle)
    testImplementation(libs.pmd.java)
    errorproneValidation(libs.errorprone.core)
    errorproneValidation(libs.nullaway)
}

tasks.test {
    // Error Prone runs inside the test JVM through the javax.tools API and needs javac internals.
    val exports = listOf("api", "file", "main", "model", "parser", "processing", "tree", "util", "code", "comp")
    jvmArgs(exports.map { "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" })
    jvmArgs(listOf("code", "comp").map { "--add-opens=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" })
    systemProperty("errorprone.processorpath", errorproneValidation.files.joinToString(File.pathSeparator))
    systemProperty("golden.update", providers.gradleProperty("goldenUpdate").getOrElse("false"))
}

description = "Core model and config generators for javacite"

// docs/rules.md is generated from the registry; checkRulesDoc fails `check` when the committed file is stale.
val rulesDocFile = rootProject.layout.projectDirectory.file("docs/rules.md")
val rulesDocMain = "io.github.wraithyy.javacite.core.docs.RulesDocGenerator"

val generateRulesDoc by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Writes docs/rules.md from rules-registry.yml."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = rulesDocMain
    args(rulesDocFile.asFile.absolutePath)
    outputs.file(rulesDocFile)
}

val checkRulesDoc by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Fails when docs/rules.md is out of date with rules-registry.yml."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = rulesDocMain
    args(rulesDocFile.asFile.absolutePath, "--check")
    inputs.file(rulesDocFile).optional()
    mustRunAfter(generateRulesDoc)
}

tasks.named("check") { dependsOn(checkRulesDoc) }
