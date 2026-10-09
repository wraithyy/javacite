package io.github.wraithyy.javacite.cli;

import io.github.wraithyy.javacite.core.generate.BuildTool;
import io.github.wraithyy.javacite.core.init.InitWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Bootstraps javacite in a Gradle or Maven project. Idempotent. */
@Command(name = "init", mixinStandardHelpOptions = true, description = "Write javacite.yml, agent files and hooks; wire the build.")
final class InitCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--dir", description = "Project root (default: current directory).")
    Path dir = Path.of("").toAbsolutePath();

    @Option(names = "--build-tool", description = "gradle or maven; required when both are present.")
    String buildTool;

    @Option(names = "--yes", description = "Non-interactive: apply the build file edit without asking.")
    boolean yes;

    @Option(names = "--no-git-hooks", description = "Skip .githooks/pre-commit and core.hooksPath.")
    boolean noGitHooks;

    @Option(names = "--targets", split = ",", description = "Agent targets, replacing the configured ones.")
    List<String> targets = List.of();

    /** Replaced in tests. */
    InputStream stdin = System.in;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Path root = dir.toAbsolutePath().normalize();
        BuildTool tool;
        try {
            tool = BuildFiles.detect(root, buildTool);
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            return 2;
        }
        String version = Main.version();
        Path buildFile = tool == BuildTool.GRADLE
                ? BuildFiles.gradleBuildFile(root).orElse(null)
                : root.resolve("pom.xml");
        boolean spring = buildFile != null && BuildFiles.read(buildFile).contains("spring-boot");
        Path config = root.resolve("javacite.yml");

        out.println("Build tool: " + tool.name().toLowerCase(java.util.Locale.ROOT));
        InitWriter.run(root, root, config, tool, BuildFiles.read(config), spring, targets, !noGitHooks, false)
                .forEach(out::println);

        if (tool == BuildTool.MAVEN) {
            out.println(mavenExtension(root, version));
        } else {
            gradleSnippet(out, buildFile, version);
        }
        out.flush();
        return 0;
    }

    private static String mavenExtension(Path root, String version) {
        Path file = root.resolve(".mvn/extensions.xml");
        String existing = BuildFiles.read(file);
        String merged = BuildFiles.mergeExtensions(existing, version);
        if (merged.equals(existing)) {
            return "unchanged .mvn/extensions.xml";
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, merged);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return (existing.isBlank() ? "created" : "updated") + " .mvn/extensions.xml";
    }

    private void gradleSnippet(PrintWriter out, Path buildFile, String version) {
        boolean kotlin = buildFile == null || buildFile.getFileName().toString().endsWith(".kts");
        String snippet = "plugins { " + BuildFiles.gradleLine(kotlin, version) + " }";
        String text = buildFile == null ? "" : BuildFiles.read(buildFile);
        if (text.contains(BuildFiles.PLUGIN_ID)) {
            out.println("unchanged " + buildFile.getFileName() + " (plugin already applied)");
            return;
        }
        Optional<String> edited = buildFile == null ? Optional.empty() : BuildFiles.insertPlugin(text, kotlin, version);
        if (edited.isEmpty()) {
            out.println("Add the plugin to your build script manually:\n  " + snippet);
            return;
        }
        out.println("Plugin snippet:\n  " + snippet);
        if (yes || confirm(out, "Append it to " + buildFile.getFileName() + "?")) {
            try {
                Files.writeString(buildFile, edited.get());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            out.println("updated " + buildFile.getFileName());
        } else {
            out.println("Skipped; add the snippet above to " + buildFile.getFileName() + " yourself.");
        }
    }

    private boolean confirm(PrintWriter out, String question) {
        out.print(question + " [y/N] ");
        out.flush();
        try {
            String line = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8)).readLine();
            return line != null && line.strip().toLowerCase(java.util.Locale.ROOT).startsWith("y");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
