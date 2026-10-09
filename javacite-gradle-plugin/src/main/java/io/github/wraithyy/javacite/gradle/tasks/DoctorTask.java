package io.github.wraithyy.javacite.gradle.tasks;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.ConfigException;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/** Prints what javacite sees: environment, config, enabled tools and which generated files are present. */
@UntrackedTask(because = "Reports on environment and files; never up to date")
public abstract class DoctorTask extends DefaultTask {

    private static final List<String> FILES = List.of(
            "AGENTS.md",
            "CLAUDE.md",
            ".cursor/rules/javacite.mdc",
            ".github/copilot-instructions.md",
            ".windsurf/rules/javacite.md",
            ".javacite/hook-format.sh",
            ".javacite/hook-check.sh",
            ".claude/settings.json",
            ".githooks/pre-commit");

    @Input
    public abstract Property<String> getConfigText();

    @Input
    public abstract Property<String> getGradleVersion();

    @Input
    public abstract Property<Boolean> getSpringOnClasspath();

    @Input
    public abstract Property<Boolean> getArchunitOnTestClasspath();

    @Internal
    public abstract RegularFileProperty getConfigFile();

    @Internal
    public abstract DirectoryProperty getRootDir();

    public static void register(Project project) {
        project.getTasks().register("javaciteDoctor", DoctorTask.class, task -> {
            task.setGroup("javacite");
            task.setDescription("Reports JDK, config, tools, generated files and hook status.");
            task.getConfigText().set(Probes.configText(project));
            task.getGradleVersion().set(project.getGradle().getGradleVersion());
            task.getSpringOnClasspath().set(Probes.springOnClasspath(project));
            task.getArchunitOnTestClasspath().set(Probes.archunitOnTestClasspath(project));
            task.getConfigFile().set(Probes.configFile(project));
            task.getRootDir().set(project.getRootProject().getLayout().getProjectDirectory());
        });
    }

    @TaskAction
    void report() {
        List<String> out = new ArrayList<>();
        String jdk = System.getProperty("java.version");
        int major = Runtime.version().feature();
        out.add("JDK: " + jdk + (major >= 21 ? " (gate ok, requires 21+)" : " (gate FAILED, requires 21+)"));
        out.add("Gradle: " + getGradleVersion().get());
        Path configPath = getConfigFile().get().getAsFile().toPath();
        String text = getConfigText().get();
        out.add("Config: " + configPath + (text.isBlank() ? " (missing, defaults used)" : ""));

        ConfigException failure = null;
        try {
            JavaciteConfig config = text.isBlank() ? ConfigLoader.defaults() : ConfigLoader.load(text);
            out.add("Config parsed: yes");
            List<String> on = new ArrayList<>();
            List<String> off = new ArrayList<>();
            for (Tool tool : Tool.values()) {
                (config.tool(tool).enabled() ? on : off).add(tool.key());
            }
            out.add("tools on: " + on + " (sonar: " + config.sonar().name().toLowerCase(java.util.Locale.ROOT) + ")");
            out.add("tools off: " + off);
            out.add("Spring (config " + config.spring().name().toLowerCase(java.util.Locale.ROOT)
                    + "): detected on classpath = " + getSpringOnClasspath().get());
        } catch (ConfigException e) {
            failure = e;
            out.add("Config parsed: NO - " + e.getMessage());
        }

        Path root = getRootDir().get().getAsFile().toPath();
        for (String rel : FILES) {
            out.add((Files.exists(root.resolve(rel)) ? "present " : "missing ") + rel);
        }
        String hooksPath = io.github.wraithyy.javacite.core.init.InitWriter.gitHooksPath(root);
        out.add("git core.hooksPath: " + (hooksPath.isEmpty() ? "not set" : hooksPath));
        out.add("javacite-archunit on test classpath: " + getArchunitOnTestClasspath().get());

        getLogger().quiet("javaciteDoctor:\n  " + String.join("\n  ", out));
        if (failure != null) {
            throw new GradleException("javacite.yml is invalid: " + failure.getMessage(), failure);
        }
    }
}
