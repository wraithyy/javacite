package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.DependencyCheckOptions;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.owasp.dependencycheck.gradle.DependencyCheckPlugin;
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension;

/** OWASP dependency-check behind the {@code javaciteAudit} task. */
public final class DependencyCheckWiring {

    private DependencyCheckWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        JavaciteConfig config = extension.getConfig().get();
        if (!config.tool(Tool.DEPENDENCY_CHECK).enabled()) {
            return;
        }
        DependencyCheckOptions options = config.dependencyCheck();
        // Applied by class: the plugin id is not resolvable from the consumer's plugin scope.
        project.getPluginManager().apply(DependencyCheckPlugin.class);

        var nvdKey = project.getProviders().environmentVariable("NVD_API_KEY");
        // Captured as a boolean so the action stays configuration-cache safe; the env var is tracked as an input.
        boolean noNvdKey = !nvdKey.isPresent();
        project.getTasks().matching(t -> t.getName().equals("dependencyCheckAnalyze")).configureEach(task -> {
            if (noNvdKey) {
                task.doFirst("javaciteNvdKeyWarning", t -> t.getLogger()
                        .warn("javacite: NVD_API_KEY is not set; dependency-check updates will be slow and rate limited."));
            }
        });
        String dataDir = System.getProperty("user.home") + "/.javacite/nvd";
        DependencyCheckExtension ext = project.getExtensions().getByType(DependencyCheckExtension.class);
        ext.getFailBuildOnCVSS().set((float) options.failOnCvss());
        ext.getAutoUpdate().set(true);
        ext.getFormats().set(List.of("XML", "HTML"));
        ext.getData().getDirectory().set(dataDir);
        ext.getNvd().getApiKey().set(nvdKey);

        project.getTasks().register("javaciteAudit", task -> {
            task.setGroup("verification");
            task.setDescription("Scans dependencies for known vulnerabilities with OWASP dependency-check.");
            task.dependsOn("dependencyCheckAnalyze");
        });

        boolean inCheck = switch (options.inCheck()) {
            case ALWAYS -> true;
            case CI -> DependencyCheckOptions.isCi(project.getProviders().environmentVariable("CI").getOrNull());
            case NEVER -> false;
        };
        if (inCheck) {
            project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(check -> check.dependsOn("javaciteAudit"));
        }
    }
}
