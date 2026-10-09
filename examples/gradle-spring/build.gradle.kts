plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.github.wraithyy.javacite")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.jspecify:jspecify:1.0.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.github.wraithyy:javacite-archunit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
