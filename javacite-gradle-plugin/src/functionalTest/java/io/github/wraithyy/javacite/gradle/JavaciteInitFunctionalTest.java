package io.github.wraithyy.javacite.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaciteInitFunctionalTest {

    @TempDir
    Path dir;

    @BeforeEach
    void project() throws IOException {
        write("settings.gradle", "rootProject.name = 'sample'\n");
        write("build.gradle", "plugins { id 'java'; id 'io.github.wraithyy.javacite' }\n");
        write("src/main/java/com/acme/App.java", "package com.acme;\npublic class App {}\n");
    }

    /** Offline stub of javacite-archunit in a file repo: enough for the classpath probe, no network or publishing. */
    private void withArchunitOnTestClasspath() throws IOException {
        Path module = dir.resolve("repo/io/github/wraithyy/javacite-archunit/0.0.1");
        Files.createDirectories(module);
        Files.writeString(
                module.resolve("javacite-archunit-0.0.1.pom"),
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>io.github.wraithyy</groupId>
                <artifactId>javacite-archunit</artifactId><version>0.0.1</version></project>
                """);
        try (var jar = new java.util.jar.JarOutputStream(
                Files.newOutputStream(module.resolve("javacite-archunit-0.0.1.jar")))) {
            jar.putNextEntry(new java.util.zip.ZipEntry("stub.txt"));
            jar.closeEntry();
        }
        write(
                "build.gradle",
                """
                plugins { id 'java'; id 'io.github.wraithyy.javacite' }
                repositories { maven { url = uri('repo') } }
                dependencies { testImplementation 'io.github.wraithyy:javacite-archunit:0.0.1' }
                """);
    }

    private void write(String name, String content) throws IOException {
        TestProjects.write(dir, name, content);
    }

    private GradleRunner runner(Map<String, String> env, String... args) {
        return TestProjects.runner(dir, "fail", env, args);
    }

    private GradleRunner runner(String... args) {
        return runner(Map.of(), args);
    }

    /** Relative path to content hash; symlinks are recorded by target. Build outputs are ignored. */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String rel = dir.relativize(f).toString();
                if (rel.startsWith("build") || rel.startsWith(".gradle") || Files.isDirectory(f)) {
                    continue;
                }
                out.put(
                        rel,
                        Files.isSymbolicLink(f)
                                ? "-> " + Files.readSymbolicLink(f)
                                : Integer.toHexString(java.util.Arrays.hashCode(Files.readAllBytes(f)))
                                        + Files.isExecutable(f));
            }
        }
        return out;
    }

    @Test
    void initWritesFilesAndIsIdempotent() throws IOException {
        withArchunitOnTestClasspath();
        BuildResult first = runner("javaciteInit").build();
        assertThat(first.getOutput()).contains("created AGENTS.md");

        for (String f : new String[] {
            "javacite.yml",
            "AGENTS.md",
            ".cursor/rules/javacite.mdc",
            ".github/copilot-instructions.md",
            ".windsurf/rules/javacite.md",
            ".javacite/hook-format.sh",
            ".javacite/hook-check.sh",
            ".claude/settings.json",
            ".githooks/pre-commit",
            "src/test/java/com/acme/ArchitectureTest.java"
        }) {
            assertThat(dir.resolve(f)).as(f).exists();
        }
        assertThat(Files.isSymbolicLink(dir.resolve("CLAUDE.md"))).isTrue();
        assertThat(Files.readSymbolicLink(dir.resolve("CLAUDE.md"))).isEqualTo(Path.of("AGENTS.md"));
        assertThat(dir.resolve(".javacite/hook-format.sh")).isExecutable();
        assertThat(dir.resolve(".javacite/hook-check.sh")).isExecutable();
        assertThat(dir.resolve(".githooks/pre-commit")).isExecutable();
        assertThat(Files.readString(dir.resolve(".claude/settings.json")))
                .contains(".javacite/hook-format.sh")
                .contains(".javacite/hook-check.sh");
        assertThat(Files.readString(dir.resolve("src/test/java/com/acme/ArchitectureTest.java")))
                .contains("package com.acme;")
                .contains("JavaciteRules.class")
                .contains("final class ArchitectureTest")
                .contains("private ArchitectureTest()");

        Map<String, String> before = snapshot();
        BuildResult second = runner("javaciteInit").build();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(second.getOutput()).doesNotContain("created ").doesNotContain("updated ");
    }

    @Test
    void initSkipsArchitectureTestWithoutArchunitOnClasspath() throws IOException {
        BuildResult result = runner("javaciteInit").build();
        assertThat(result.getOutput()).contains("javacite-archunit is not on the test classpath");
        assertThat(dir.resolve("src/test/java/com/acme/ArchitectureTest.java")).doesNotExist();
    }

    @Test
    void initKeepsExistingAgentsText() throws IOException {
        write("AGENTS.md", "# My project\n\nKeep me.\n");
        runner("javaciteInit").build();
        runner("javaciteInit").build();
        String agents = Files.readString(dir.resolve("AGENTS.md"));
        assertThat(agents).contains("Keep me.");
        assertThat(agents.split("<!-- javacite:start -->", -1)).hasSize(2);
    }

    @Test
    void initPreservesUnrelatedHook() throws IOException {
        write(
                ".claude/settings.json",
                """
                {"hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"my-own-hook.sh"}]}]}}
                """);
        runner("javaciteInit").build();
        assertThat(Files.readString(dir.resolve(".claude/settings.json")))
                .contains("my-own-hook.sh")
                .contains(".javacite/hook-check.sh");
    }

    @Test
    void doctorReportsToolsAndJdk() {
        BuildResult result = runner("javaciteDoctor").build();
        assertThat(result.getOutput()).contains("JDK").contains("tools on").contains("Config parsed: yes");
    }

    @Test
    void sonarProfileHasRules() throws IOException {
        runner("javaciteSonarProfile").build();
        assertThat(Files.readString(dir.resolve("build/javacite/sonar-profile.xml")))
                .contains("<rule>");
    }

    @Test
    void sonarWiredOnlyWithEnvironment() {
        BuildResult without = runner("tasks", "--all").build();
        assertThat(without.getOutput()).doesNotContain("javaciteSonarProperties");

        BuildResult with = runner(Map.of("SONAR_HOST_URL", "http://localhost:9000"), "javaciteSonarProperties", "tasks", "--all")
                .build();
        assertThat(with.getOutput())
                .containsPattern("sonar\\.java\\.checkstyle\\.reportPaths=/\\S+/build/reports/checkstyle/main\\.xml")
                .contains("sonar.java.source=17")
                .containsPattern("(?m)^sonar - ");
    }
}
