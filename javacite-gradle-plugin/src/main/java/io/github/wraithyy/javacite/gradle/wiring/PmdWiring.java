package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import io.github.wraithyy.javacite.gradle.JavaciteVersions;
import io.github.wraithyy.javacite.gradle.tasks.GenerateToolConfigsTask;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.Pmd;
import org.gradle.api.plugins.quality.PmdExtension;
import org.gradle.api.tasks.TaskProvider;

/**
 * PMD with the generated ruleset. Gradle's Pmd task has no severity gate: every reported violation fails
 * the build, so a rule set to {@code warn} is reported (priority 3) but also fails. Use {@code off} to silence.
 */
public final class PmdWiring {

    private static final int ALL_PRIORITIES = 5;

    private PmdWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        if (!extension.getConfig().get().tool(Tool.PMD).enabled()) {
            return;
        }
        TaskProvider<GenerateToolConfigsTask> generate = GenerateToolConfigsTask.register(project);

        project.getPluginManager().apply("pmd");
        PmdExtension pmd = project.getExtensions().getByType(PmdExtension.class);
        pmd.setToolVersion(JavaciteVersions.PMD);
        pmd.setRuleSets(List.of());
        pmd.setRuleSetFiles(project.files(
                generate.flatMap(GenerateToolConfigsTask::getOutputDir).map(d -> d.file("pmd.xml"))));
        pmd.getRulesMinimumPriority().set(ALL_PRIORITIES);
        pmd.getIncrementalAnalysis().set(true);
        pmd.setIgnoreFailures(false);
        pmd.setConsoleOutput(true);

        project.getTasks().withType(Pmd.class).configureEach(task -> {
            task.dependsOn(generate);
            task.getReports().getXml().getRequired().set(true);
        });
        project.getTasks().named("javaciteCheck").configure(t -> t.dependsOn("pmdMain", "pmdTest"));
    }
}
