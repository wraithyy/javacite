package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import io.github.wraithyy.javacite.gradle.JavaciteVersions;
import io.github.wraithyy.javacite.gradle.tasks.GenerateToolConfigsTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/** Checkstyle with the generated config; any warning or error fails the build. */
public final class CheckstyleWiring {

    private CheckstyleWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        if (!extension.getConfig().get().tool(Tool.CHECKSTYLE).enabled()) {
            return;
        }
        TaskProvider<GenerateToolConfigsTask> generate = GenerateToolConfigsTask.register(project);
        Provider<RegularFile> configFile =
                generate.flatMap(GenerateToolConfigsTask::getOutputDir).map(d -> d.file("checkstyle.xml"));

        project.getPluginManager().apply("checkstyle");
        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        checkstyle.setToolVersion(JavaciteVersions.CHECKSTYLE);
        checkstyle.setConfig(project.getResources().getText().fromFile(configFile));
        checkstyle.setMaxWarnings(0);
        checkstyle.setIgnoreFailures(false);

        project.getTasks().withType(Checkstyle.class).configureEach(task -> {
            task.dependsOn(generate);
            task.getReports().getXml().getRequired().set(true);
            task.getReports().getHtml().getRequired().set(true);
        });
        project.getTasks().named("javaciteCheck").configure(t -> t.dependsOn("checkstyleMain", "checkstyleTest"));
    }
}
