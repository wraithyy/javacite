package io.github.wraithyy.javacite.maven.mojo;

import io.github.wraithyy.javacite.core.config.ConfigException;
import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.init.InitWriter;
import io.github.wraithyy.javacite.maven.JavaciteSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Prints what javacite sees: environment, config, enabled tools and which generated files are present. */
@Mojo(name = "doctor", requiresProject = true, aggregator = true, threadSafe = true)
public class DoctorMojo extends AbstractMojo {

    private static final List<String> FILES = List.of(
            "AGENTS.md",
            "CLAUDE.md",
            ".cursor/rules/javacite.mdc",
            ".github/copilot-instructions.md",
            ".windsurf/rules/javacite.md",
            ".javacite/hook-format.sh",
            ".javacite/hook-check.sh",
            ".claude/settings.json",
            ".githooks/pre-commit",
            ".mvn/extensions.xml");

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        List<String> out = new ArrayList<>();
        int major = Runtime.version().feature();
        out.add("JDK: " + System.getProperty("java.version")
                + (major >= 21 ? " (gate ok, requires 21+)" : " (gate FAILED, requires 21+)"));
        out.add("Maven: " + session.getSystemProperties().getProperty("maven.version", "unknown"));
        Path root = JavaciteSupport.rootDir(session);
        Path configPath = JavaciteSupport.configPath(session);
        String failure = null;
        try {
            String text = Files.exists(configPath) ? Files.readString(configPath, StandardCharsets.UTF_8) : "";
            out.add("Config: " + configPath + (text.isBlank() ? " (missing, defaults used)" : ""));
            JavaciteConfig config = text.isBlank() ? ConfigLoader.defaults() : ConfigLoader.load(text);
            out.add("Config parsed: yes");
            List<String> on = new ArrayList<>();
            List<String> off = new ArrayList<>();
            for (Tool tool : Tool.values()) {
                (config.tool(tool).enabled() ? on : off).add(tool.key());
            }
            out.add("tools on: " + on + " (sonar: " + config.sonar().name().toLowerCase(Locale.ROOT) + ")");
            out.add("tools off: " + off);
            out.add("Spring (config " + config.spring().name().toLowerCase(Locale.ROOT)
                    + "): detected in dependencies = " + JavaciteSupport.springDetected(project));
        } catch (ConfigException | IOException e) {
            failure = e.getMessage();
            out.add("Config parsed: NO - " + failure);
        }
        for (String rel : FILES) {
            out.add((Files.exists(root.resolve(rel)) ? "present " : "missing ") + rel);
        }
        String hooksPath = InitWriter.gitHooksPath(root);
        out.add("git core.hooksPath: " + (hooksPath.isEmpty() ? "not set" : hooksPath));
        out.add("javacite-archunit declared as dependency: " + JavaciteSupport.archunitDeclared(project));
        getLog().info("javacite doctor:\n  " + String.join("\n  ", out));
        if (failure != null) {
            throw new MojoExecutionException("javacite.yml is invalid: " + failure);
        }
    }
}
