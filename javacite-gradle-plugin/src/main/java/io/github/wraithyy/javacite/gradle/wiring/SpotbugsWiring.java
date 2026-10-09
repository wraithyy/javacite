package io.github.wraithyy.javacite.gradle.wiring;

import com.github.spotbugs.snom.Confidence;
import com.github.spotbugs.snom.Effort;
import com.github.spotbugs.snom.SpotBugsExtension;
import com.github.spotbugs.snom.SpotBugsTask;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import io.github.wraithyy.javacite.gradle.JavaciteVersions;
import io.github.wraithyy.javacite.gradle.tasks.GenerateToolConfigsTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/** SpotBugs at max effort; the XML report is required for Sonar import. */
public final class SpotbugsWiring {

    // SpotBugs needs org.apache.commons.lang3.Strings (3.18+); the version lives in libs.versions.toml.
    private static final String COMMONS_LANG3_GROUP = "org.apache.commons";
    private static final String COMMONS_LANG3_MODULE = "commons-lang3";

    private SpotbugsWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        if (!extension.getConfig().get().tool(Tool.SPOTBUGS).enabled()) {
            return;
        }
        TaskProvider<GenerateToolConfigsTask> generate = GenerateToolConfigsTask.register(project);

        project.getPluginManager().apply("com.github.spotbugs");
        SpotBugsExtension spotbugs = project.getExtensions().getByType(SpotBugsExtension.class);
        spotbugs.getToolVersion().set(JavaciteVersions.SPOTBUGS);
        spotbugs.getEffort().set(Effort.MAX);
        spotbugs.getReportLevel().set(Confidence.MEDIUM);
        spotbugs.getIgnoreFailures().set(false);
        spotbugs.getExcludeFilter()
                .set(generate.flatMap(GenerateToolConfigsTask::getOutputDir).map(d -> d.file("spotbugs-exclude.xml")));

        // A dependency-management BOM (e.g. Spring Boot) otherwise downgrades commons-lang3 on the tool classpath, and
        // its rule outranks resolutionStrategy.force. Registering our rule just before resolution makes it run last.
        project.getConfigurations().matching(c -> c.getName().equals("spotbugs")).configureEach(c -> c.getIncoming()
                .beforeResolve(r -> c.getResolutionStrategy().eachDependency(d -> {
                    if (COMMONS_LANG3_GROUP.equals(d.getRequested().getGroup())
                            && COMMONS_LANG3_MODULE.equals(d.getRequested().getName())) {
                        d.useVersion(JavaciteVersions.COMMONS_LANG3);
                        d.because("SpotBugs needs commons-lang3 with Strings");
                    }
                })));

        project.getTasks().withType(SpotBugsTask.class).configureEach(task -> {
            task.dependsOn(generate);
            task.getReports().create("xml", report -> report.getRequired().set(true));
        });
        project.getTasks().named("javaciteCheck").configure(t -> t.dependsOn("spotbugsMain"));
    }
}
