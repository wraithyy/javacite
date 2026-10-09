package io.github.wraithyy.javacite.core.init;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.generate.AgentsMdGenerator;
import io.github.wraithyy.javacite.core.generate.BuildTool;
import io.github.wraithyy.javacite.core.generate.ClaudeHooksGenerator;
import io.github.wraithyy.javacite.core.generate.EditorRulesGenerator;
import io.github.wraithyy.javacite.core.generate.GitHooksGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Build-tool-agnostic part of {@code javacite init}: writes config, agent rule files, hooks and the architecture test. */
public final class InitWriter {

    private InitWriter() {}

    public static final String CONFIG_TEMPLATE =
            """
            # javacite configuration. Every tool is on by default; switch one off with `off`.
            # Docs: https://github.com/wraithyy/javacite#readme
            version: 1
            tools:
              spotless: on
              errorprone: on
              checkstyle: on
              pmd: on
              spotbugs: on
              archunit: on
              jacoco: on
              dependencyCheck: on
              enforcer: on
              sonar: auto
            spring: auto
            rules: {}
            """;

    /**
     * Idempotent bootstrap. {@code configText} blank means the default template; {@code springDetected} resolves
     * {@code spring: auto}; the architecture test is written only when {@code archunitOnTestClasspath}, since it
     * cannot compile without javacite-archunit; a non-empty {@code targetsOverride} replaces the configured agent targets.
     *
     * @return one status line per touched path ("created ...", "updated ...", "unchanged ...") plus hints
     */
    public static List<String> run(
            Path root,
            Path projectDir,
            Path configPath,
            BuildTool buildTool,
            String configText,
            boolean springDetected,
            List<String> targetsOverride,
            boolean gitHooks,
            boolean archunitOnTestClasspath) {
        List<String> report = new ArrayList<>();
        if (Files.notExists(configPath)) {
            report.add(write(configPath, CONFIG_TEMPLATE, false, root));
        }
        JavaciteConfig config = ConfigLoader.load(configText.isBlank() ? CONFIG_TEMPLATE : configText);
        boolean spring =
                switch (config.spring()) {
                    case ON -> true;
                    case OFF -> false;
                    case AUTO -> springDetected;
                };
        List<String> targets = new ArrayList<>(targetsOverride.isEmpty() ? config.agentTargets() : targetsOverride);
        if (!gitHooks) {
            targets.remove("git-hooks");
        }
        String block = AgentsMdGenerator.render(ResolvedRules.of(config, spring), buildTool, spring);

        EditorRulesGenerator.render(block, targets, rel -> read(root.resolve(rel)))
                .forEach((rel, content) -> report.add(write(root.resolve(rel), content, false, root)));
        if (targets.contains("claude-md")) {
            report.add(claudeMd(root, block));
        }
        boolean spotless = config.tool(Tool.SPOTLESS).enabled();
        if (targets.contains("claude-hooks")) {
            ClaudeHooksGenerator.scripts(buildTool, spotless)
                    .forEach((rel, content) -> report.add(write(root.resolve(rel), content, true, root)));
            Path settings = root.resolve(".claude/settings.json");
            String merged = ClaudeHooksGenerator.mergeSettingsJson(read(settings).orElse(""));
            report.add(write(settings, merged.endsWith("\n") ? merged : merged + "\n", false, root));
        }
        if (targets.contains("git-hooks")) {
            report.add(write(
                    root.resolve(GitHooksGenerator.PRE_COMMIT_PATH), GitHooksGenerator.preCommit(buildTool, spotless), true, root));
            report.add(activateGitHooks(root));
        }
        if (config.tool(Tool.ARCHUNIT).enabled()) {
            report.add(
                    archunitOnTestClasspath
                            ? architectureTest(root, projectDir, spring)
                            : "hint: javacite-archunit is not on the test classpath, ArchitectureTest not written;"
                                    + " add it as a test dependency and re-run init");
        }
        return report;
    }

    private static String claudeMd(Path root, String block) {
        Path claude = root.resolve("CLAUDE.md");
        if (Files.exists(claude, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(claude)) {
                return "unchanged CLAUDE.md (symlink)";
            }
            return write(claude, AgentsMdGenerator.merge(read(claude).orElse(""), block), false, root)
                    + " (existing regular CLAUDE.md, block merged instead of symlinking)";
        }
        try {
            Files.createSymbolicLink(claude, Path.of("AGENTS.md"));
            return "created CLAUDE.md -> AGENTS.md";
        } catch (IOException | UnsupportedOperationException e) {
            // Windows without symlink privilege: fall back to a copy of the block.
            return write(claude, AgentsMdGenerator.merge("", block), false, root) + " (symlink unavailable)";
        }
    }

    private static String activateGitHooks(Path root) {
        if (Files.notExists(root.resolve(".git"))) {
            return "skipped core.hooksPath (no .git directory)";
        }
        String current = gitHooksPath(root);
        if (GitHooksGenerator.activationCommand().get(3).equals(current)) {
            return "unchanged core.hooksPath";
        }
        if (!current.isEmpty()) {
            // Husky, lefthook and friends own this setting; overriding it would silently disable their hooks.
            return "hint: core.hooksPath is '" + current + "', left unchanged; call "
                    + GitHooksGenerator.PRE_COMMIT_PATH + " from that hook manager";
        }
        try {
            Process p = new ProcessBuilder(GitHooksGenerator.activationCommand())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0 ? "updated core.hooksPath" : "skipped core.hooksPath (git exited " + p.exitValue() + ")";
        } catch (IOException e) {
            return "skipped core.hooksPath (git unavailable: " + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "skipped core.hooksPath (interrupted)";
        }
    }

    /** Current {@code core.hooksPath} of the repo at {@code root}, or an empty string when unset or git is missing. */
    public static String gitHooksPath(Path root) {
        if (Files.notExists(root.resolve(".git"))) {
            return "";
        }
        try {
            Process p = new ProcessBuilder("git", "config", "core.hooksPath")
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return p.waitFor() == 0 ? out : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static String architectureTest(Path root, Path projectDir, boolean spring) {
        Optional<String> base = basePackage(projectDir.resolve("src/main/java"));
        if (base.isEmpty()) {
            return "hint: no sources under src/main/java, ArchitectureTest not written";
        }
        Path file = projectDir.resolve("src/test/java/" + base.get().replace('.', '/') + "/ArchitectureTest.java");
        if (Files.exists(file)) {
            return "unchanged " + root.relativize(file) + " (exists, left untouched)";
        }
        return write(file, architectureTestSource(base.get(), spring), false, root);
    }

    public static String architectureTestSource(String basePackage, boolean spring) {
        StringBuilder sb = new StringBuilder()
                .append("package ").append(basePackage).append(";\n\n")
                .append("import com.tngtech.archunit.core.importer.ImportOption;\n")
                .append("import com.tngtech.archunit.junit.AnalyzeClasses;\n")
                .append("import com.tngtech.archunit.junit.ArchTest;\n")
                .append("import com.tngtech.archunit.junit.ArchTests;\n")
                .append("import io.github.wraithyy.javacite.archunit.JavaciteRules;\n");
        if (spring) {
            sb.append("import io.github.wraithyy.javacite.archunit.JavaciteSpringRules;\n");
        }
        sb.append("\n/** Enforces the javacite architecture rules; generated by javacite init. */\n")
                .append("@AnalyzeClasses(packages = \"").append(basePackage)
                .append("\", importOptions = ImportOption.DoNotIncludeTests.class)\n")
                .append("final class ArchitectureTest {\n\n")
                .append("    @ArchTest\n    static final ArchTests JAVACITE = ArchTests.in(JavaciteRules.class);\n");
        if (spring) {
            sb.append("\n    @ArchTest\n    static final ArchTests SPRING = ArchTests.in(JavaciteSpringRules.class);\n");
        }
        sb.append("\n    private ArchitectureTest() {}\n");
        return sb.append("}\n").toString();
    }

    /** Deepest package shared by all sources; empty when there are none or they sit in the default package. */
    public static Optional<String> basePackage(Path sources) {
        if (!Files.isDirectory(sources)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(sources)) {
            List<Path> dirs = files.filter(f -> f.toString().endsWith(".java"))
                    .map(f -> sources.relativize(f).getParent())
                    .toList();
            if (dirs.isEmpty() || dirs.contains(null)) {
                return Optional.empty();
            }
            List<String> common = null;
            for (Path dir : dirs) {
                List<String> parts = new ArrayList<>();
                dir.forEach(p -> parts.add(p.toString()));
                if (common == null) {
                    common = parts;
                } else {
                    int n = 0;
                    while (n < common.size() && n < parts.size() && common.get(n).equals(parts.get(n))) {
                        n++;
                    }
                    common = new ArrayList<>(common.subList(0, n));
                }
            }
            return common == null || common.isEmpty() ? Optional.empty() : Optional.of(String.join(".", common));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> read(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Why {@code file} must not be written (symlink, or a parent resolving outside {@code root}), else null. */
    private static String unsafeTarget(Path file, Path root) {
        Path abs = file.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(abs)) {
            return "target is a symlink";
        }
        try {
            Path ancestor = abs.getParent();
            // The directory may not exist yet; its nearest existing ancestor decides where the write lands.
            while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor == null || !ancestor.toRealPath().startsWith(root.toRealPath())) {
                return "resolves outside the project";
            }
            return null;
        } catch (IOException e) {
            return "cannot resolve parent: " + e.getMessage();
        }
    }

    /** Writes only when content or executable bit differ; returns a one-line status. */
    public static String write(Path file, String content, boolean executable, Path root) {
        String name = root.relativize(file.toAbsolutePath().normalize()).toString();
        String unsafe = unsafeTarget(file, root);
        if (unsafe != null) {
            return "warning: skipped " + name + " (" + unsafe + ")";
        }
        try {
            Optional<String> existing = read(file);
            boolean same = existing.isPresent() && existing.get().equals(content);
            if (!same) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, content);
            }
            boolean permFixed = false;
            if (executable && !Files.isExecutable(file)) {
                permFixed = file.toFile().setExecutable(true, false);
            }
            if (same && !permFixed) {
                return "unchanged " + name;
            }
            return (existing.isPresent() ? "updated " : "created ") + name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
