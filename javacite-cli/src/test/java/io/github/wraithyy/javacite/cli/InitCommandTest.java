package io.github.wraithyy.javacite.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wraithyy.javacite.core.generate.BuildTool;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InitCommandTest {

    @TempDir
    Path dir;

    private record Run(int exit, String out) {}

    private Run run(String... args) {
        StringWriter sw = new StringWriter();
        CommandLine cl = new CommandLine(new Main());
        cl.setOut(new PrintWriter(sw));
        cl.setErr(new PrintWriter(sw));
        int exit = cl.execute(args);
        return new Run(exit, sw.toString());
    }

    @Test
    void detectsBuildTool() throws IOException {
        assertThatThrownBy(() -> BuildFiles.detect(dir, null)).hasMessageContaining("No build.gradle");
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        assertThat(BuildFiles.detect(dir, null)).isEqualTo(BuildTool.MAVEN);
        Files.writeString(dir.resolve("build.gradle.kts"), "");
        assertThatThrownBy(() -> BuildFiles.detect(dir, null)).hasMessageContaining("--build-tool");
        assertThat(BuildFiles.detect(dir, "gradle")).isEqualTo(BuildTool.GRADLE);
    }

    @Test
    void insertsPluginAfterPluginsBlockOnly() {
        String kts = "plugins {\n    java\n}\n";
        assertThat(BuildFiles.insertPlugin(kts, true, "1.0").orElseThrow())
                .contains("plugins {\n    id(\"io.github.wraithyy.javacite\") version \"1.0\"\n    java");
        assertThat(BuildFiles.insertPlugin("plugins {\n id 'java'\n}\n", false, "1.0").orElseThrow())
                .contains("id 'io.github.wraithyy.javacite' version '1.0'");
        assertThat(BuildFiles.insertPlugin("plugins { java }\n", true, "1.0")).isEmpty();
        assertThat(BuildFiles.insertPlugin("apply(plugin = \"java\")\n", true, "1.0")).isEmpty();
        assertThat(BuildFiles.insertPlugin(kts.replace("java", "id(\"io.github.wraithyy.javacite\")"), true, "1.0"))
                .isEmpty();
    }

    @Test
    void mergesExtensionsXml() {
        String fresh = BuildFiles.mergeExtensions("", "1.0");
        assertThat(fresh).contains("<artifactId>javacite-maven-plugin</artifactId>");
        assertThat(BuildFiles.mergeExtensions(fresh, "1.0")).isEqualTo(fresh);
        String other = "<extensions>\n    <extension><groupId>a</groupId></extension>\n</extensions>\n";
        String merged = BuildFiles.mergeExtensions(other, "1.0");
        assertThat(merged).contains("<groupId>a</groupId>").contains("javacite-maven-plugin").endsWith("</extensions>\n");
    }

    @Test
    void gradleInitIsIdempotent() throws IOException {
        Files.writeString(dir.resolve("build.gradle.kts"), "plugins {\n    java\n}\n");
        Run first = run("init", "--dir", dir.toString(), "--yes", "--no-git-hooks");
        assertThat(first.exit()).isZero();
        assertThat(first.out()).contains("created javacite.yml", "created AGENTS.md", "updated build.gradle.kts");
        assertThat(Files.readString(dir.resolve("build.gradle.kts"))).contains("io.github.wraithyy.javacite");
        assertThat(dir.resolve(".githooks")).doesNotExist();

        Run second = run("init", "--dir", dir.toString(), "--yes", "--no-git-hooks");
        assertThat(second.exit()).isZero();
        assertThat(second.out()).contains("unchanged AGENTS.md", "plugin already applied");
        assertThat(second.out()).doesNotContain("created ", "updated ");
    }

    @Test
    void gradleWithoutPluginsBlockPrintsInstructions() throws IOException {
        Files.writeString(dir.resolve("build.gradle.kts"), "apply(plugin = \"java\")\n");
        Run r = run("init", "--dir", dir.toString(), "--yes", "--no-git-hooks");
        assertThat(r.out()).contains("Add the plugin to your build script manually");
        assertThat(Files.readString(dir.resolve("build.gradle.kts"))).doesNotContain("javacite");
    }

    @Test
    void mavenInitWritesExtensionOnce() throws IOException {
        Files.writeString(dir.resolve("pom.xml"), "<project><artifactId>spring-boot-starter</artifactId></project>");
        Run first = run("init", "--dir", dir.toString(), "--yes", "--no-git-hooks");
        assertThat(first.exit()).isZero();
        assertThat(first.out()).contains("created .mvn/extensions.xml");
        assertThat(Files.readString(dir.resolve("AGENTS.md"))).containsIgnoringCase("spring");
        Run second = run("init", "--dir", dir.toString(), "--yes", "--no-git-hooks");
        assertThat(second.out()).contains("unchanged .mvn/extensions.xml").doesNotContain("created ");
    }

    @Test
    void errorsWhenNothingDetected() {
        Run r = run("init", "--dir", dir.toString(), "--yes");
        assertThat(r.exit()).isEqualTo(2);
        assertThat(r.out()).contains("error:");
    }

    @Test
    void doctorFailsOnlyOnBrokenConfig() throws IOException {
        assertThat(run("doctor", "--dir", dir.toString()).exit()).isZero();
        Files.writeString(dir.resolve("javacite.yml"), "bogus: 1\n");
        Run r = run("doctor", "--dir", dir.toString());
        assertThat(r.exit()).isEqualTo(1);
        assertThat(r.out()).contains("FAIL javacite.yml");
    }
}
