package io.github.wraithyy.javacite.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavacitePluginFunctionalTest {

    /** JAVACITE_TEST_JDK17, else macOS java_home; empty when no JDK 17 is available. */
    private static Optional<String> jdk17Home() {
        String env = System.getenv("JAVACITE_TEST_JDK17");
        if (env != null && !env.isBlank()) {
            return Optional.of(env);
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder("/usr/libexec/java_home", "-v", "17")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && !out.isEmpty() ? Optional.of(out) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @TempDir
    Path dir;

    @BeforeEach
    void project() throws IOException {
        write("settings.gradle", "rootProject.name = 'sample'\n");
        write("build.gradle", "plugins { id 'java'; id 'io.github.wraithyy.javacite' }\nrepositories { mavenCentral() }\n");
        write("src/main/java/demo/Hello.java", "package demo;\n\n/** Fixture that passes every javacite check. */\npublic final class Hello {\n    private Hello() {}\n}\n");
    }

    private void write(String name, String content) throws IOException {
        TestProjects.write(dir, name, content);
    }

    private GradleRunner runner(String... args) {
        return TestProjects.runner(dir, "all", Map.of(), args);
    }

    @Test
    void appliesWithoutConfigFile() {
        BuildResult result = runner("javaciteCheck").build();
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void releaseComesFromConfig() throws IOException {
        write("javacite.yml", "java: 17\n");
        runner("compileJava").build();
        try (InputStream in = Files.newInputStream(dir.resolve("build/classes/java/main/demo/Hello.class"));
                DataInputStream data = new DataInputStream(in)) {
            data.readInt();
            data.readUnsignedShort();
            assertThat(data.readUnsignedShort()).isEqualTo(61);
        }
    }

    @Test
    void invalidConfigFailsBuild() throws IOException {
        write("javacite.yml", "bogus_key: 1\n");
        BuildResult result = runner("compileJava").buildAndFail();
        assertThat(result.getOutput()).contains("javacite.yml: ").contains("bogus_key").doesNotContain("at io.github");
    }

    @Test
    void initAndDoctorLiveOnRootOnly() throws IOException {
        write("settings.gradle", "rootProject.name = 'sample'\ninclude 'app'\n");
        write("app/build.gradle", "plugins { id 'java'; id 'io.github.wraithyy.javacite' }\n");
        assertThat(runner(":app:javaciteInit").buildAndFail().getOutput()).contains("not found");
        assertThat(runner("javaciteDoctor").build().getOutput()).contains("javaciteDoctor");
    }

    @Test
    void configurationCacheReusedAndInvalidatedByConfigEdit() throws IOException {
        write("javacite.yml", "java: 17\n");
        runner("javaciteCheck").build();
        assertThat(runner("javaciteCheck").build().getOutput()).contains("Reusing configuration cache.");
        write("javacite.yml", "java: 21\n");
        assertThat(runner("javaciteCheck").build().getOutput())
                .doesNotContain("Reusing configuration cache.")
                .contains("Calculating task graph");
    }

    @Test
    void failsClearlyOnJdkBelow21() {
        String jdk17 = jdk17Home().orElse(null);
        assumeTrue(jdk17 != null, "no JDK 17: set JAVACITE_TEST_JDK17");
        BuildResult result = runner("javaciteCheck", "-Dorg.gradle.java.home=" + jdk17).buildAndFail();
        assertThat(result.getOutput()).contains("javacite requires JDK 21").contains("toolchain");
    }
}
