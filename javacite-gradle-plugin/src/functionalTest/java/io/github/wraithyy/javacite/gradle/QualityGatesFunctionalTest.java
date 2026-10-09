package io.github.wraithyy.javacite.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityGatesFunctionalTest {

    @TempDir
    Path dir;

    @BeforeEach
    void project() throws IOException {
        write("settings.gradle", "rootProject.name = 'sample'\n");
        write("build.gradle", """
                plugins { id 'java'; id 'io.github.wraithyy.javacite' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation platform('org.junit:junit-bom:5.11.4')
                    testImplementation 'org.junit.jupiter:junit-jupiter'
                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                }
                test { useJUnitPlatform() }
                """);
        // Covered: constructor + a(); uncovered: two lines of b() -> 50% line coverage.
        write("src/main/java/Calc.java", """
                public class Calc {
                    public int a() {
                        return 1;
                    }
                    public int b() {
                        int x = 2;
                        return x;
                    }
                }
                """);
        write("src/test/java/CalcTest.java", """
                import org.junit.jupiter.api.Test;
                class CalcTest {
                    @Test
                    void a() {
                        new Calc().a();
                    }
                }
                """);
    }

    private void write(String name, String content) throws IOException {
        TestProjects.write(dir, name, content);
    }

    private GradleRunner runner(Map<String, String> env, String... args) {
        return TestProjects.runner(dir, "all", env, args);
    }

    @Test
    void coverageBelowMinFailsCheck() throws IOException {
        write("javacite.yml", "tools:\n  jacoco: { min: 0.8 }\n  spotless: off\n  errorprone: off\n  checkstyle: off\n  pmd: off\n  spotbugs: off\n");
        BuildResult result = runner(Map.of(), "check").buildAndFail();
        assertThat(result.getOutput()).contains("Rule violated for bundle").contains("lines covered ratio");
    }

    @Test
    void coverageAboveMinPassesAndWritesXml() throws IOException {
        write("javacite.yml", "tools:\n  jacoco: { min: 0.4 }\n  spotless: off\n  errorprone: off\n  checkstyle: off\n  pmd: off\n  spotbugs: off\n");
        runner(Map.of(), "check").build();
        assertThat(dir.resolve("build/reports/jacoco/test/jacocoTestReport.xml")).exists();
    }

    @Test
    void jacocoOffRemovesTasks() throws IOException {
        write("javacite.yml", "tools:\n  jacoco: off\n");
        BuildResult result = runner(Map.of(), "tasks", "--all").build();
        assertThat(result.getOutput()).doesNotContain("jacocoTestReport");
    }

    @Test
    void bannedDependencyFailsResolution() throws IOException {
        write("javacite.yml", "deps:\n  ban: [commons-lang:commons-lang]\n");
        Files.writeString(
                dir.resolve("build.gradle"),
                Files.readString(dir.resolve("build.gradle")) + "dependencies { implementation 'commons-lang:commons-lang:2.6' }\n");
        BuildResult result = runner(Map.of(), "compileJava").buildAndFail();
        assertThat(result.getOutput())
                .contains("javacite: dependency commons-lang:commons-lang is banned by javacite.yml deps.ban");
    }

    @Test
    void auditExistsButJoinsCheckOnlyInCi() {
        assertThat(runnerWithoutCi("tasks", "--all").build().getOutput()).contains("javaciteAudit");
        BuildResult noCi = runnerWithoutCi("check", "--dry-run").build();
        assertThat(noCi.getOutput()).doesNotContain("dependencyCheckAnalyze");
        BuildResult ci = runner(Map.of("CI", "true"), "check", "--dry-run").build();
        assertThat(ci.getOutput()).contains(":dependencyCheckAnalyze").contains(":javaciteAudit");
    }

    private GradleRunner runnerWithoutCi(String... args) {
        Map<String, String> env = new java.util.HashMap<>(System.getenv());
        env.remove("CI");
        return runner(Map.of(), args).withDebug(false).withEnvironment(env);
    }
}
