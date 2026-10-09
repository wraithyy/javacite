pluginManagement {
    // Resolves io.github.wraithyy.javacite from the repo root instead of a published artifact.
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Substitutes io.github.wraithyy:javacite-archunit with the root project.
includeBuild("../..")

rootProject.name = "gradle-spring"
