package io.github.wraithyy.javacite.gradle.tasks;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.generate.SonarProfileGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Writes the Sonar quality profile backup that mirrors the enabled javacite rules. */
@CacheableTask
public abstract class SonarProfileTask extends DefaultTask {

    static final String PROFILE_NAME = "javacite";

    @Input
    public abstract Property<String> getConfigText();

    @Input
    public abstract Property<Boolean> getSpringOnClasspath();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public static void register(Project project) {
        project.getTasks().register("javaciteSonarProfile", SonarProfileTask.class, task -> {
            task.setGroup("javacite");
            task.setDescription("Writes build/javacite/sonar-profile.xml for import into Sonar.");
            task.getConfigText().set(Probes.configText(project));
            task.getSpringOnClasspath().set(Probes.springOnClasspath(project));
            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("javacite/sonar-profile.xml"));
        });
    }

    @TaskAction
    void write() throws IOException {
        String text = getConfigText().get();
        JavaciteConfig config = text.isBlank() ? ConfigLoader.defaults() : ConfigLoader.load(text);
        String xml = SonarProfileGenerator.generate(
                ResolvedRules.of(config, getSpringOnClasspath().get()), PROFILE_NAME);
        Path out = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, xml);
        } catch (IOException e) {
            throw new GradleException("Cannot write " + out, e);
        }
    }
}
