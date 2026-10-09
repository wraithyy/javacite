package io.github.wraithyy.javacite.gradle.tasks;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.generate.CheckstyleXmlGenerator;
import io.github.wraithyy.javacite.core.generate.PmdRulesetGenerator;
import io.github.wraithyy.javacite.core.generate.SpotbugsFilterGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import io.github.wraithyy.javacite.gradle.wiring.SpringDetector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/** Writes the Checkstyle, PMD and SpotBugs configs derived from javacite.yml into {@code build/javacite/}. */
@CacheableTask
public abstract class GenerateToolConfigsTask extends DefaultTask {

    public static final String NAME = "generateJavaciteConfigs";

    @Input
    public abstract Property<String> getYaml();

    @Input
    public abstract Property<Boolean> getSpringDetected();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** Registers the task once per project; later callers get the same provider. */
    public static TaskProvider<GenerateToolConfigsTask> register(Project project) {
        if (project.getTasks().getNames().contains(NAME)) {
            return project.getTasks().named(NAME, GenerateToolConfigsTask.class);
        }
        return project.getTasks().register(NAME, GenerateToolConfigsTask.class, task -> {
            task.setGroup("javacite");
            task.setDescription("Generates Checkstyle, PMD and SpotBugs configs from javacite.yml.");
            task.getYaml().set(Probes.configText(project));
            task.getSpringDetected().set(SpringDetector.detected(project));
            task.getOutputDir().set(project.getLayout().getBuildDirectory().dir("javacite"));
        });
    }

    @TaskAction
    void generate() throws IOException {
        String yaml = getYaml().get();
        JavaciteConfig config = yaml.isBlank() ? ConfigLoader.defaults() : ConfigLoader.load(yaml);
        boolean spring = getSpringDetected().get();
        ResolvedRules rules = ResolvedRules.of(config, spring);
        Path out = getOutputDir().get().getAsFile().toPath();
        Files.createDirectories(out);
        write(out.resolve("checkstyle.xml"), new CheckstyleXmlGenerator().generate(rules));
        write(out.resolve("pmd.xml"), new PmdRulesetGenerator().generate(rules));
        write(out.resolve("spotbugs-exclude.xml"), new SpotbugsFilterGenerator().generate(rules));
        write(out.resolve("spring.detected"), Boolean.toString(spring));
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
