package io.github.wraithyy.javacite.core.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HooksGeneratorsTest {

    private record Result(int exit, String out) {}

    private static Result run(Path dir, Map<String, String> env, String stdin, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        pb.environment().putAll(env);
        Process p = pb.start();
        p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).isTrue();
        return new Result(p.exitValue(), out);
    }

    private static boolean available(String... cmd) {
        try {
            return new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor() >= 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        assertThat(p.toFile().setExecutable(true)).isTrue();
        return p;
    }

    @Test
    void scriptsHaveValidSyntaxAndCommands(@TempDir Path dir) throws Exception {
        assumeTrue(available("sh", "-c", "true"));
        for (BuildTool tool : BuildTool.values()) {
            Map<String, String> all = new java.util.LinkedHashMap<>(ClaudeHooksGenerator.scripts(tool));
            all.put(GitHooksGenerator.PRE_COMMIT_PATH, GitHooksGenerator.preCommit(tool));
            for (Map.Entry<String, String> e : all.entrySet()) {
                assertThat(e.getValue()).startsWith("#!/bin/sh").doesNotContain("{{");
                Path f = write(dir, tool + "/" + e.getKey(), e.getValue());
                assertThat(run(dir, Map.of(), "", "sh", "-n", f.toString()).exit()).as(e.getKey()).isZero();
            }
        }
        assertThat(ClaudeHooksGenerator.scripts(BuildTool.GRADLE).get(ClaudeHooksGenerator.FORMAT_SCRIPT))
                .contains("./gradlew -q spotlessApply -PspotlessIdeHook=\"$FILE\"");
        assertThat(ClaudeHooksGenerator.scripts(BuildTool.MAVEN).get(ClaudeHooksGenerator.CHECK_SCRIPT))
                .contains("mvn -q -DskipTests verify");
    }

    @Test
    void formatHookRunsOnJavaOnly(@TempDir Path dir) throws Exception {
        assumeTrue(available("sh", "-c", "true"));
        Path script = write(dir, "hook-format.sh", ClaudeHooksGenerator.scripts(BuildTool.GRADLE).get(ClaudeHooksGenerator.FORMAT_SCRIPT));
        Path log = dir.resolve("log");
        Path stub = write(dir, "stub.sh", "#!/bin/sh\nprintf '%s\\n' \"$1\" >> '" + log + "'\n");
        Path java = Files.writeString(dir.resolve("Foo Bar.java"), "class Foo {}");
        Path txt = Files.writeString(dir.resolve("notes.txt"), "x");
        Map<String, String> env = Map.of("JAVACITE_FORMAT_CMD", stub.toString());

        String payload = "{\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"%s\"}}";
        assertThat(run(dir, env, payload.formatted(txt), "sh", script.toString()).exit()).isZero();
        assertThat(run(dir, env, payload.formatted(dir.resolve("Missing.java")), "sh", script.toString()).exit()).isZero();
        assertThat(log).doesNotExist();

        assertThat(run(dir, env, payload.formatted(java), "sh", script.toString()).exit()).isZero();
        assertThat(Files.readString(log).strip()).isEqualTo(java.toRealPath().toString());
    }

    @Test
    void formatHookSedFallbackWithoutJq(@TempDir Path dir) throws Exception {
        assumeTrue(available("sh", "-c", "true"));
        Path script = write(dir, "hook-format.sh", ClaudeHooksGenerator.scripts(BuildTool.GRADLE).get(ClaudeHooksGenerator.FORMAT_SCRIPT));
        Path log = dir.resolve("log");
        Path stub = write(dir, "stub.sh", "#!/bin/sh\nprintf '%s\\n' \"$1\" >> '" + log + "'\n");
        Path java = Files.writeString(dir.resolve("Foo.java"), "class Foo {}");
        // The sed fallback is exercised by making `command -v jq` fail via an empty PATH dir plus absolute sh/tools.
        Path bin = dir.resolve("bin");
        Files.createDirectories(bin);
        for (String tool : new String[] {"sed", "head", "cat", "printf"}) {
            for (String root : new String[] {"/usr/bin/", "/bin/"}) {
                Path real = Path.of(root + tool);
                if (Files.exists(real) && !Files.exists(bin.resolve(tool))) {
                    Files.createSymbolicLink(bin.resolve(tool), real);
                }
            }
        }
        String payload = "{\"tool_input\":{\"file_path\":\"%s\"}}".formatted(java);

        Result r = run(dir, Map.of("JAVACITE_FORMAT_CMD", stub.toString(), "PATH", bin.toString()), payload, "/bin/sh", script.toString());

        assertThat(r.exit()).isZero();
        assertThat(Files.readString(log).strip()).isEqualTo(java.toRealPath().toString());
    }

    @Test
    void checkHookExitCodes(@TempDir Path dir) throws Exception {
        assumeTrue(available("sh", "-c", "true"));
        Path script = write(dir, "hook-check.sh", ClaudeHooksGenerator.scripts(BuildTool.GRADLE).get(ClaudeHooksGenerator.CHECK_SCRIPT));
        Path fail = write(dir, "fail.sh", "#!/bin/sh\nfor i in $(seq 1 60); do echo line$i; done\nexit 1\n");
        Map<String, String> failing = Map.of("JAVACITE_CHECK_CMD", fail.toString());
        Map<String, String> passing = Map.of("JAVACITE_CHECK_CMD", "true");

        assertThat(run(dir, passing, "{}", "sh", script.toString()).exit()).isZero();
        assertThat(run(dir, failing, "{\"stop_hook_active\": true}", "sh", script.toString()).exit()).isZero();
        Result r = run(dir, failing, "{\"stop_hook_active\": false}", "sh", script.toString());
        assertThat(r.exit()).isEqualTo(2);
        assertThat(r.out()).contains("line60").contains("line21").doesNotContain("line20\n");
    }

    @Test
    void preCommitFormatsAndRestagesJava(@TempDir Path dir) throws Exception {
        assumeTrue(available("git", "--version"));
        assumeTrue(available("sh", "-c", "true"));
        run(dir, Map.of(), "", "git", "init", "-q");
        Path hook = write(dir, ".githooks/pre-commit", GitHooksGenerator.preCommit(BuildTool.MAVEN));
        Path stub = write(dir.resolve("tools"), "fmt.sh", "#!/bin/sh\necho '// formatted' >> \"$1\"\n");
        Files.writeString(dir.resolve("A.java"), "class A {}\n");
        Files.writeString(dir.resolve("B.txt"), "b\n");
        run(dir, Map.of(), "", "git", "add", "A.java", "B.txt");
        Map<String, String> env = Map.of("JAVACITE_FORMAT_CMD", stub.toString(), "JAVACITE_CHECK_CMD", "true");

        assertThat(run(dir, env, "", "sh", hook.toString()).exit()).isZero();
        assertThat(Files.readString(dir.resolve("A.java"))).endsWith("// formatted\n");
        assertThat(Files.readString(dir.resolve("B.txt"))).isEqualTo("b\n");
        assertThat(run(dir, Map.of(), "", "git", "diff", "--name-only").out()).isEmpty();

        Map<String, String> failingCheck = Map.of("JAVACITE_FORMAT_CMD", stub.toString(), "JAVACITE_CHECK_CMD", "false");
        assertThat(run(dir, failingCheck, "", "sh", hook.toString()).exit()).isNotZero();
        assertThat(GitHooksGenerator.activationCommand()).containsExactly("git", "config", "core.hooksPath", ".githooks");
    }

    @Test
    void preCommitBatchesStagedFilesIntoOneInvocation(@TempDir Path dir) throws Exception {
        assumeTrue(available("git", "--version"));
        assumeTrue(available("sh", "-c", "true"));
        run(dir, Map.of(), "", "git", "init", "-q");
        // A stub gradlew records its args; the hook must call it exactly once for all staged files.
        Path log = dir.resolve("calls.log");
        write(dir, "gradlew", "#!/bin/sh\nprintf '%s\\n' \"$*\" >> '" + log + "'\n");
        Path hook = write(dir, ".githooks/pre-commit", GitHooksGenerator.preCommit(BuildTool.GRADLE));
        Files.writeString(dir.resolve("A.java"), "class A {}\n");
        Files.createDirectories(dir.resolve("sub dir"));
        Files.writeString(dir.resolve("sub dir/B+1.java"), "class B {}\n");
        run(dir, Map.of(), "", "git", "add", "A.java", "sub dir/B+1.java");
        Map<String, String> env = Map.of("JAVACITE_CHECK_CMD", "true");

        assertThat(run(dir, env, "", "sh", hook.toString()).exit()).isZero();

        java.util.List<String> calls = Files.readAllLines(log);
        assertThat(calls).hasSize(1);
        String top = dir.toRealPath().toString().replaceAll("[.\\\\+*?^$()\\[\\]{}|]", "\\\\$0");
        assertThat(calls.get(0))
                .contains("spotlessApply", "-PspotlessFiles=")
                .contains(top + "/A\\.java|")
                .contains(top + "/sub dir/B\\+1\\.java");
        assertThat(GitHooksGenerator.preCommit(BuildTool.MAVEN)).contains("-DspotlessFiles=\"$REGEX\"");
    }

    @Test
    void settingsMergeIsIdempotentAndPreservesKeys() {
        String existing = """
                {
                  "model": "opus",
                  "permissions": {"allow": ["Bash(ls)"]},
                  "hooks": {
                    "PostToolUse": [{"matcher": "Bash", "hooks": [{"type": "command", "command": "other.sh"}]}],
                    "SessionStart": [{"hooks": [{"type": "command", "command": "start.sh"}]}]
                  }
                }
                """;

        String once = ClaudeHooksGenerator.mergeSettingsJson(existing);
        String twice = ClaudeHooksGenerator.mergeSettingsJson(once);

        assertThat(twice).isEqualTo(once);
        assertThat(once).contains("\"model\": \"opus\"", "Bash(ls)", "other.sh", "start.sh");
        assertThat(once).contains("\"matcher\": \"Edit|Write\"", ClaudeHooksGenerator.FORMAT_SCRIPT, ClaudeHooksGenerator.CHECK_SCRIPT);
        assertThat(once).contains("\"if\": \"Edit(*.java)\"", "\"\\\"$CLAUDE_PROJECT_DIR\\\"/.javacite/hook-check.sh\"");
        assertThat(once.split(ClaudeHooksGenerator.CHECK_SCRIPT, -1)).hasSize(2);
        assertThat(ClaudeHooksGenerator.mergeSettingsJson(null))
                .isEqualTo(ClaudeHooksGenerator.mergeSettingsJson(ClaudeHooksGenerator.mergeSettingsJson("")));
    }

    @Test
    void settingsEntriesCarryTimeoutsInSeconds() {
        String once = ClaudeHooksGenerator.mergeSettingsJson("");
        assertThat(once).contains("\"timeout\": 120", "\"timeout\": 600");
        // A re-run upgrades an entry written without a timeout.
        String legacy = once.replaceAll(",\\s*\"timeout\": \\d+", "");
        assertThat(legacy).doesNotContain("timeout");
        assertThat(ClaudeHooksGenerator.mergeSettingsJson(legacy)).isEqualTo(once);
    }

    @Test
    void preCommitKeepsMultiWordFormatCommandsAndNonAsciiNames(@TempDir Path dir) throws Exception {
        assumeTrue(available("git", "--version"));
        run(dir, Map.of(), "", "git", "init", "-q");
        Path hook = write(dir, ".githooks/pre-commit", GitHooksGenerator.preCommit(BuildTool.MAVEN));
        Files.writeString(dir.resolve("p\u0159\u00edli\u0161 \u017elu\u0165ou\u010dk\u00fd.java"), "class A {}\n");
        Files.writeString(dir.resolve("old.java"), "class O {}\n");
        run(dir, Map.of(), "", "git", "add", "-A");
        run(dir, Map.of(), "", "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "i", "--no-verify");
        Files.move(dir.resolve("old.java"), dir.resolve("renamed.java"));
        run(dir, Map.of(), "", "git", "add", "-A");
        Map<String, String> env = Map.of("JAVACITE_FORMAT_CMD", "echo fmt", "JAVACITE_CHECK_CMD", "true");

        Result r = run(dir, env, "", "sh", hook.toString());

        assertThat(r.exit()).as(r.out()).isZero();
        assertThat(r.out()).doesNotContain("not found");
    }

    @Test
    void formatHookRejectsPathsOutsideProjectAndOptionLikeNames(@TempDir Path dir) throws Exception {
        assumeTrue(available("sh", "-c", "true"));
        Path project = Files.createDirectories(dir.resolve("proj")).toRealPath();
        Path script = write(dir, "hook-format.sh", ClaudeHooksGenerator.scripts(BuildTool.GRADLE).get(ClaudeHooksGenerator.FORMAT_SCRIPT));
        Path log = dir.resolve("log");
        Path stub = write(dir, "stub.sh", "#!/bin/sh\nprintf '%s\\n' \"$1\" >> '" + log + "'\n");
        Path outside = Files.writeString(dir.resolve("Out.java"), "class O {}");
        Files.writeString(project.resolve("-rf.java"), "class R {}");
        Files.writeString(project.resolve("In.java"), "class I {}");
        Map<String, String> env = Map.of("JAVACITE_FORMAT_CMD", stub.toString(), "CLAUDE_PROJECT_DIR", project.toString());
        String payload = "{\"tool_input\":{\"file_path\":\"%s\"}}";

        run(dir, env, payload.formatted(outside), "sh", script.toString());
        run(dir, env, payload.formatted("../Out.java"), "sh", script.toString());
        assertThat(log).doesNotExist();

        run(dir, env, payload.formatted("In.java"), "sh", script.toString());
        run(dir, env, payload.formatted("-rf.java"), "sh", script.toString());
        assertThat(Files.readAllLines(log)).containsExactly(project + "/In.java", project + "/-rf.java");
    }
}
