package io.github.wraithyy.javacite.gradle.tasks;

import io.github.wraithyy.javacite.core.generate.BuildTool;
import io.github.wraithyy.javacite.core.init.InitWriter;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/** Bootstraps javacite in a project: config file, agent rule files, hooks and the architecture test. */
@UntrackedTask(because = "Writes files across the project tree and merges into user-owned files")
public abstract class InitTask extends DefaultTask {

    @Input
    public abstract Property<String> getConfigText();

    @Input
    public abstract Property<Boolean> getSpringOnClasspath();

    @Input
    public abstract Property<Boolean> getArchunitOnTestClasspath();

    @Internal
    public abstract RegularFileProperty getConfigFile();

    @Internal
    public abstract DirectoryProperty getRootDir();

    @Internal
    public abstract DirectoryProperty getProjectDir();

    public static void register(Project project) {
        project.getTasks().register("javaciteInit", InitTask.class, task -> {
            task.setGroup("javacite");
            task.setDescription("Writes javacite.yml, agent rule files, hooks and ArchitectureTest (idempotent).");
            task.getConfigText().set(Probes.configText(project));
            task.getSpringOnClasspath().set(Probes.springOnClasspath(project));
            task.getArchunitOnTestClasspath().set(Probes.archunitOnTestClasspath(project));
            task.getConfigFile().set(Probes.configFile(project));
            task.getRootDir().set(project.getRootProject().getLayout().getProjectDirectory());
            task.getProjectDir().set(project.getLayout().getProjectDirectory());
        });
    }

    @TaskAction
    void init() {
        Path root = getRootDir().get().getAsFile().toPath();
        Path projectDir = getProjectDir().get().getAsFile().toPath();
        Path configPath = getConfigFile().get().getAsFile().toPath();
        List<String> report = InitWriter.run(
                root,
                projectDir,
                configPath,
                BuildTool.GRADLE,
                getConfigText().get(),
                getSpringOnClasspath().get(),
                List.of(),
                true,
                getArchunitOnTestClasspath().get());
        getLogger().quiet("javaciteInit summary:\n  " + String.join("\n  ", report));
    }
}
