package io.github.wraithyy.javacite.gradle.tasks;

import io.github.wraithyy.javacite.core.sonar.SonarProjectProperties;
import io.github.wraithyy.javacite.core.sonar.SonarProperties;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes {@code build/javacite/sonar-project.properties} for a standalone {@code sonar-scanner} run. Nothing here
 * invokes a scanner; the file only collects what the build already knows (sources, classes, classpaths, reports).
 */
@CacheableTask
public abstract class SonarPropertiesTask extends DefaultTask {

    public static final String NAME = "javaciteSonarProperties";

    @Input
    public abstract Property<String> getProjectKey();

    @Input
    public abstract Property<String> getBaseDir();

    @Input
    public abstract Property<Integer> getJavaVersion();

    /** Absolute paths of existing {@code src/main/java} directories. */
    @Input
    public abstract ListProperty<String> getSources();

    @Input
    public abstract ListProperty<String> getTests();

    @Input
    public abstract ListProperty<String> getBinaries();

    @Input
    public abstract ListProperty<String> getTestBinaries();

    @Input
    public abstract ListProperty<String> getPmdReports();

    @Input
    public abstract ListProperty<String> getJacocoReports();

    @Input
    public abstract ListProperty<String> getJunitReports();

    @Classpath
    public abstract ConfigurableFileCollection getLibraries();

    @Classpath
    public abstract ConfigurableFileCollection getTestLibraries();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** Registers the task on the root project and collects inputs from every Java project, now or later. */
    public static void register(Project root, Provider<Integer> javaVersion) {
        root.getTasks().register(NAME, SonarPropertiesTask.class, task -> {
            task.setGroup("javacite");
            task.setDescription("Writes build/javacite/sonar-project.properties for a standalone sonar-scanner run.");
            task.getProjectKey().set(root.getName());
            task.getBaseDir().set(root.getProjectDir().getAbsolutePath());
            task.getJavaVersion().set(javaVersion);
            task.getOutputFile().set(root.getLayout().getBuildDirectory().file("javacite/sonar-project.properties"));
            for (Project p : root.getAllprojects()) {
                p.getPluginManager().withPlugin("java", ignored -> task.collect(p));
            }
        });
    }

    private void collect(Project p) {
        SourceSet main = p.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName("main");
        SourceSet test = p.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName("test");
        File projectDir = p.getProjectDir();
        getSources().addAll(p.provider(() -> existing(projectDir, "src/main/java")));
        getTests().addAll(p.provider(() -> existing(projectDir, "src/test/java")));
        getBinaries().addAll(p.provider(() -> paths(main.getOutput().getClassesDirs().getFiles())));
        getTestBinaries().addAll(p.provider(() -> paths(test.getOutput().getClassesDirs().getFiles())));
        getLibraries().from(p.getConfigurations().getByName(main.getCompileClasspathConfigurationName()).getIncoming().getFiles());
        getTestLibraries().from(p.getConfigurations().getByName(test.getCompileClasspathConfigurationName()).getIncoming().getFiles());
        String buildDir = p.getLayout().getBuildDirectory().get().getAsFile().getAbsolutePath();
        Map<String, String> reports = SonarProperties.forGradle(buildDir);
        getPmdReports().add(reports.get("sonar.java.pmd.reportPaths"));
        getJacocoReports().add(reports.get("sonar.coverage.jacoco.xmlReportPaths"));
        getJunitReports().add(buildDir + "/test-results/test");
    }

    private static List<String> existing(File projectDir, String relative) {
        File dir = new File(projectDir, relative);
        return dir.isDirectory() ? List.of(dir.getAbsolutePath()) : List.of();
    }

    private static List<String> paths(Iterable<File> files) {
        List<String> out = new ArrayList<>();
        files.forEach(f -> out.add(f.getAbsolutePath()));
        return out;
    }

    @TaskAction
    void write() throws IOException {
        Path base = Path.of(getBaseDir().get()).toAbsolutePath().normalize();
        Map<String, String> props = new TreeMap<>();
        props.put("sonar.projectKey", getProjectKey().get());
        props.put("sonar.projectBaseDir", base.toString());
        props.put("sonar.sources", join(base, getSources().get()));
        props.put("sonar.tests", join(base, getTests().get()));
        props.put("sonar.java.binaries", join(base, getBinaries().get()));
        props.put("sonar.java.test.binaries", join(base, getTestBinaries().get()));
        props.put("sonar.java.libraries", join(base, files(getLibraries())));
        props.put("sonar.java.test.libraries", join(base, files(getTestLibraries())));
        props.put("sonar.java.source", String.valueOf(getJavaVersion().get()));
        props.put("sonar.java.pmd.reportPaths", join(base, getPmdReports().get()));
        props.put("sonar.coverage.jacoco.xmlReportPaths", join(base, getJacocoReports().get()));
        props.put("sonar.junit.reportPaths", join(base, getJunitReports().get()));

        Path out = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, SonarProjectProperties.render(props), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Cannot write " + out, e);
        }
    }

    private static List<String> files(ConfigurableFileCollection collection) {
        return paths(collection.getFiles());
    }

    /** Paths under the base dir become relative (the scanner may run in a container with a different mount point). */
    private static String join(Path base, List<String> values) {
        return values.stream()
                .map(v -> {
                    Path abs = Path.of(v).toAbsolutePath().normalize();
                    return abs.startsWith(base) ? base.relativize(abs).toString().replace('\\', '/') : abs.toString().replace('\\', '/');
                })
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }
}
