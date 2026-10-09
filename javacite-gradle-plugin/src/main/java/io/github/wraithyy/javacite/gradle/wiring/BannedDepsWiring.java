package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

/** Fails resolution of any configuration that pulls a coordinate listed in {@code deps.ban}. */
public final class BannedDepsWiring {

    // Tool-internal classpaths are the plugin's business, not the user's dependency graph.
    private static final Set<String> TOOL_CONFIGURATIONS = Set.of(
            "errorprone", "pmd", "jacocoAgent", "jacocoAnt", "dependencyCheckAnalyze", "dependencyCheckAggregate", "dependencyCheckUpdate",
            "dependencyCheckPurge");

    private BannedDepsWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        JavaciteConfig config = extension.getConfig().get();
        Set<String> banned = Set.copyOf(config.bannedDeps());
        if (banned.isEmpty()) {
            return;
        }
        project.getConfigurations().configureEach(configuration -> {
            if (TOOL_CONFIGURATIONS.contains(configuration.getName())) {
                return;
            }
            configuration.getResolutionStrategy().eachDependency(details -> {
                String coordinate = details.getRequested().getGroup() + ":" + details.getRequested().getName();
                if (banned.contains(coordinate)) {
                    throw new GradleException(
                            "javacite: dependency " + coordinate + " is banned by javacite.yml deps.ban");
                }
            });
        });
    }
}
