package io.github.wraithyy.javacite.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Seeds one violation per tool into a small Spring-less project and checks javaciteCheck fails on it. */
class ToolWiringFunctionalTest {

    private static final String CLEAN =
            """
            package demo;

            /** Seed. */
            public final class Greeter {
                private Greeter() {}

                /**
                 * Seed.
                 *
                 * @param name the name
                 * @return the result
                 */
                public static String greet(final String name) {
                    return "Hello " + name;
                }
            }
            """;

    @TempDir
    Path dir;

    @BeforeEach
    void project() throws IOException {
        write("settings.gradle", "rootProject.name = 'sample'\n");
        write("build.gradle", """
                plugins { id 'java'; id 'io.github.wraithyy.javacite' }
                repositories { mavenCentral() }
                """);
        write("src/main/java/demo/Greeter.java", CLEAN);
    }

    private void write(String name, String content) throws IOException {
        TestProjects.write(dir, name, content);
    }

    private BuildResult run(boolean expectFailure, String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--console=plain";
        all[1] = "--continue";
        System.arraycopy(args, 0, all, 2, args.length);
        // spotbugs-gradle-plugin 6.5.12 (latest) calls Configuration.setVisible, deprecated in Gradle 9.8, so
        // --warning-mode=fail is impossible while it is applied.
        GradleRunner runner = TestProjects.runner(dir, "all", Map.of(), all);
        return expectFailure ? runner.buildAndFail() : runner.build();
    }

    private void assertSeedFails(String path, String source, String failedTask, String marker) throws IOException {
        write(path, source);
        BuildResult result = run(true, "javaciteCheck");
        assertThat(result.task(failedTask).getOutcome()).as(result.getOutput()).isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput()).contains(marker);
    }

    @Test
    void cleanFixturePasses() {
        BuildResult result = run(false, "javaciteCheck");
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void unformattedFileFailsSpotless() throws IOException {
        assertSeedFails(
                "src/main/java/demo/Messy.java",
                "package demo;\npublic class Messy {   int x ;}\n",
                ":spotlessJavaCheck", "spotlessApply");
    }

    @Test
    void missingFinalParameterFailsCheckstyle() throws IOException {
        assertSeedFails(
                "src/main/java/demo/Params.java",
                """
                package demo;

                /** Seed. */
                public final class Params {
                    private Params() {}

                    /**
                     * Seed.
                     *
                     * @param n the n
                     * @return the result
                     */
                    public static int twice(int n) {
                        return n * 2;
                    }
                }
                """,
                ":checkstyleMain", "[FinalParameters]");
    }

    @Test
    void emptyCatchFailsPmd() throws IOException {
        write("javacite.yml", "rules:\n  errorprone.EmptyCatch: off\n");
        assertSeedFails(
                "src/main/java/demo/Swallow.java",
                """
                package demo;

                /** Seed. */
                public final class Swallow {
                    private Swallow() {}

                    private static void risky() throws java.io.IOException {}

                    /** Seed. */
                    public static void run() {
                        try {
                            risky();
                        } catch (java.io.IOException e) {
                        }
                    }
                }
                """,
                ":pmdMain", "EmptyCatchBlock");
    }

    @Test
    void randomUsedOnceFailsSpotbugs() throws IOException {
        assertSeedFails(
                "src/main/java/demo/Dice.java",
                """
                package demo;

                import java.util.Random;

                /** Seed. */
                public final class Dice {
                    private Dice() {}

                    /**
                     * Seed.
                     *
                     * @return the result
                     */
                    public static int roll() {
                        return new Random().nextInt();
                    }
                }
                """,
                ":spotbugsMain", "SpotBugs");
    }

    @Test
    void spotbugsClasspathOverridesBomDowngradeOfCommonsLang3() throws IOException {
        write("build.gradle", """
                plugins { id 'java'; id 'io.github.wraithyy.javacite' }
                repositories { mavenCentral() }
                // Mimics io.spring.dependency-management downgrading the tool classpath.
                configurations.matching { it.name == 'spotbugs' }.configureEach {
                    resolutionStrategy.eachDependency {
                        if (it.requested.name == 'commons-lang3') { it.useVersion('3.17.0') }
                    }
                }
                tasks.register('showLang3') {
                    notCompatibleWithConfigurationCache('resolves a configuration in the task action')
                    doLast {
                        println 'LANG3=' + configurations.spotbugs.resolvedConfiguration.resolvedArtifacts
                                .findAll { it.name == 'commons-lang3' }*.moduleVersion*.id*.version
                    }
                }
                """);
        BuildResult result = run(false, "showLang3");
        assertThat(result.getOutput()).contains("LANG3=[3.20.0]");
    }

    @Test
    void missingOverrideFailsErrorProneUnlessRuleOff() throws IOException {
        String source =
                """
                package demo;

                /** Seed. */
                public final class Task implements Runnable {
                    /** Seed. */
                    public void run() {}
                }
                """;
        assertSeedFails("src/main/java/demo/Task.java", source, ":compileJava", "[MissingOverride]");
        write("javacite.yml", "rules:\n  errorprone.MissingOverride: off\n");
        assertThat(run(false, "compileJava").task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void nullDereferenceFailsNullAway() throws IOException {
        write(
                "src/main/java/demo/package-info.java",
                "@NullMarked\npackage demo;\n\nimport org.jspecify.annotations.NullMarked;\n");
        assertSeedFails(
                "src/main/java/demo/Maybe.java",
                """
                package demo;

                import org.jspecify.annotations.Nullable;

                /** Seed. */
                public final class Maybe {
                    private Maybe() {}

                    static @Nullable String find() {
                        return null;
                    }

                    /**
                     * Seed.
                     *
                     * @return the result
                     */
                    public static int length() {
                        return find().length();
                    }
                }
                """,
                ":compileJava", "[NullAway]");
    }

    @Test
    void pmdOffRemovesTask() throws IOException {
        write("javacite.yml", "tools:\n  pmd: off\n");
        BuildResult result = run(false, "tasks", "--all");
        assertThat(result.getOutput()).doesNotContain("pmdMain").contains("checkstyleMain");
    }

    @Test
    void spotlessIdeHookFormatsOnlyThatFile() throws IOException {
        Path hooked = dir.toRealPath().resolve("src/main/java/demo/A.java");
        Path other = dir.resolve("src/main/java/demo/B.java");
        String messy = "package demo;\npublic class %s {   int x ;}\n";
        write("src/main/java/demo/A.java", messy.formatted("A"));
        write("src/main/java/demo/B.java", messy.formatted("B"));
        run(false, "spotlessApply", "-PspotlessIdeHook=" + hooked);
        assertThat(Files.readString(hooked)).doesNotContain("   int x ;");
        assertThat(Files.readString(other)).contains("   int x ;");
    }

    @Test
    void springDetectedFromClasspath() throws IOException {
        write("build.gradle", """
                plugins { id 'java'; id 'io.github.wraithyy.javacite' }
                repositories { mavenCentral() }
                dependencies { implementation 'org.springframework.boot:spring-boot:3.5.16' }
                """);
        run(false, "generateJavaciteConfigs");
        assertThat(Files.readString(dir.resolve("build/javacite/spring.detected"))).isEqualTo("true");
    }

    @Test
    void springNotDetectedWithoutBoot() {
        run(false, "generateJavaciteConfigs");
        assertThat(dir.resolve("build/javacite/spring.detected")).hasContent("false");
    }
}
