plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Version is pinned here because precompiled script plugins cannot use the version catalog.
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.37.0")
}
