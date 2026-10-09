plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    // Central Portal (not legacy OSSRH); credentials come from ORG_GRADLE_PROJECT_mavenCentralUsername/Password.
    publishToMavenCentral()

    // Local dev has no key: sign only when one is configured. A release must never ship unsigned.
    val hasKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    val release = providers.gradleProperty("javacite.release").orNull == "true"
    if (hasKey) {
        signAllPublications()
    } else if (release) {
        throw GradleException("javacite.release=true but no signing key (signingInMemoryKey) is configured")
    }

    pom {
        name = project.name
        description = providers.provider { project.description ?: "javacite: strict Java guardrails for agent-driven development" }
        inceptionYear = "2026"
        url = "https://github.com/wraithyy/javacite"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "wraithyy"
                name = "wraithyy"
                url = "https://github.com/wraithyy"
            }
        }
        scm {
            url = "https://github.com/wraithyy/javacite"
            connection = "scm:git:git://github.com/wraithyy/javacite.git"
            developerConnection = "scm:git:ssh://git@github.com/wraithyy/javacite.git"
        }
    }
}
