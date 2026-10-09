package io.github.wraithyy.javacite.core.init;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wraithyy.javacite.core.generate.BuildTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitWriterTest {

    @TempDir
    Path dir;

    private List<String> run(boolean archunitOnClasspath) throws IOException {
        Path src = dir.resolve("src/main/java/com/acme/App.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "package com.acme;\npublic class App {}\n");
        return InitWriter.run(
                dir, dir, dir.resolve("javacite.yml"), BuildTool.GRADLE, "", false, List.of(), false, archunitOnClasspath);
    }

    @Test
    void templateSatisfiesOwnStyleRules() {
        String src = InitWriter.architectureTestSource("com.acme", true);
        assertThat(src)
                .contains("/** Enforces")
                .contains("final class ArchitectureTest")
                .contains("static final ArchTests JAVACITE")
                .contains("static final ArchTests SPRING");
        assertThat(src.indexOf("static final ArchTests")).isLessThan(src.indexOf("private ArchitectureTest()"));
    }

    @Test
    void writesArchitectureTestOnlyWhenArchunitIsOnClasspath() throws IOException {
        List<String> without = run(false);
        assertThat(dir.resolve("src/test/java/com/acme/ArchitectureTest.java")).doesNotExist();
        assertThat(without).anyMatch(l -> l.startsWith("hint:") && l.contains("javacite-archunit"));

        run(true);
        assertThat(dir.resolve("src/test/java/com/acme/ArchitectureTest.java")).exists();
    }

    @Test
    void basePackageUsesAllSources() throws IOException {
        for (String f : List.of("com/acme/a/A", "com/acme/b/B", "com/acme/c/C", "com/acme/d/D", "com/acme/e/E", "com/acme/f/F")) {
            Path p = dir.resolve("src/main/java/" + f + ".java");
            Files.createDirectories(p.getParent());
            Files.writeString(p, "class X {}");
        }
        assertThat(InitWriter.basePackage(dir.resolve("src/main/java"))).contains("com.acme");
        Path other = dir.resolve("src/main/java/org/zzz/Z.java");
        Files.createDirectories(other.getParent());
        Files.writeString(other, "class Z {}");
        // "org" sorts after the first five files, so a first-five sample would still say com.acme.
        assertThat(InitWriter.basePackage(dir.resolve("src/main/java"))).isEmpty();
    }

    @Test
    void writeRefusesSymlinkedTargetsAndEscapingParents() throws IOException {
        Path outside = Files.createTempDirectory("javacite-outside");
        Path victim = outside.resolve("victim.txt");
        Files.writeString(victim, "keep");
        Path root = dir.toRealPath();
        Files.createSymbolicLink(root.resolve("link.txt"), victim);
        Files.createSymbolicLink(root.resolve(".claude"), outside);

        assertThat(InitWriter.write(root.resolve("link.txt"), "pwned", false, root)).startsWith("warning: skipped");
        assertThat(InitWriter.write(root.resolve(".claude/settings.json"), "{}", false, root))
                .startsWith("warning: skipped");
        assertThat(Files.readString(victim)).isEqualTo("keep");
        assertThat(outside.resolve("settings.json")).doesNotExist();
        assertThat(InitWriter.write(root.resolve("ok/new.txt"), "x", false, root)).startsWith("created");
    }

    @Test
    void spotlessOffGeneratesCheckOnlyHooks() throws IOException {
        Files.writeString(dir.resolve("javacite.yml"), "tools:\n  spotless: off\n");
        InitWriter.run(
                dir, dir, dir.resolve("javacite.yml"), BuildTool.GRADLE, "tools:\n  spotless: off\n", false,
                List.of("claude-hooks", "git-hooks"), true, false);
        assertThat(Files.readString(dir.resolve(".githooks/pre-commit"))).doesNotContain("spotlessApply", "git add");
        assertThat(Files.readString(dir.resolve(".javacite/hook-format.sh"))).doesNotContain("gradlew").contains("exit 0");
    }

    @Test
    void existingForeignHooksPathIsLeftAlone() throws Exception {
        Process init = new ProcessBuilder("git", "init", "-q").directory(dir.toFile()).start();
        org.junit.jupiter.api.Assumptions.assumeTrue(init.waitFor() == 0);
        new ProcessBuilder("git", "config", "core.hooksPath", ".husky").directory(dir.toFile()).start().waitFor();

        List<String> report = InitWriter.run(
                dir, dir, dir.resolve("javacite.yml"), BuildTool.GRADLE, "", false, List.of("git-hooks"), true, false);

        assertThat(InitWriter.gitHooksPath(dir)).isEqualTo(".husky");
        assertThat(report).anyMatch(l -> l.startsWith("hint: core.hooksPath is '.husky'"));
    }
}
