package io.github.wraithyy.javacite.gradle.tasks;

import io.github.wraithyy.javacite.gradle.wiring.SpringDetector;
import java.util.function.Function;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

/** Lazy, configuration-cache-friendly inputs shared by the javacite tasks. */
public final class Probes {

    private Probes() {}

    /** Config is always the root project's javacite.yml. */
    public static RegularFile configFile(Project project) {
        return project.getRootProject().getLayout().getProjectDirectory().file("javacite.yml");
    }

    /** Raw javacite.yml text, empty when the file is absent; parsing happens in the task action. */
    public static Provider<String> configText(Project project) {
        return project.getProviders()
                .fileContents(configFile(project))
                .getAsText()
                .orElse("");
    }

    /** True when any Java project in the build has spring-boot on its compileClasspath. */
    static Provider<Boolean> springOnClasspath(Project root) {
        return anyJavaProject(root.getAllprojects(), root, SpringDetector::detected);
    }

    /** Root-only: ArchitectureTest is written into the root project. */
    static Provider<Boolean> archunitOnTestClasspath(Project root) {
        return anyJavaProject(java.util.Set.of(root), root, p -> onTestClasspath(p, "javacite-archunit"));
    }

    /**
     * Collects one provider per Java project as the java plugin appears (possibly after this call), at configuration
     * time, so nothing touches {@code Project} when the task runs.
     */
    private static Provider<Boolean> anyJavaProject(
            Iterable<Project> projects, Project owner, Function<Project, Provider<Boolean>> probe) {
        ListProperty<Boolean> flags = owner.getObjects().listProperty(Boolean.class);
        for (Project p : projects) {
            p.getPluginManager().withPlugin("java", ignored -> flags.add(probe.apply(p)));
        }
        return flags.map(list -> list.contains(Boolean.TRUE));
    }

    /** Lenient view so an unresolvable dependency never breaks init or doctor. */
    private static Provider<Boolean> onTestClasspath(Project project, String prefix) {
        return project.getConfigurations()
                .getByName("testCompileClasspath")
                .getIncoming()
                .artifactView(view -> view.setLenient(true))
                .getArtifacts()
                .getResolvedArtifacts()
                .map(artifacts -> artifacts.stream()
                        .map(a -> a.getId().getComponentIdentifier())
                        .anyMatch(id -> isArchunit(id, prefix)));
    }

    /** Matches published modules and includeBuild/project substitutions (which carry no group). */
    private static boolean isArchunit(ComponentIdentifier id, String name) {
        if (id instanceof ModuleComponentIdentifier m) {
            return m.getGroup().equals("io.github.wraithyy") && m.getModule().startsWith(name);
        }
        return id instanceof ProjectComponentIdentifier p && p.getProjectName().startsWith(name);
    }
}
