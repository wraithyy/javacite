package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.spring.SpringArtifacts;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.provider.Provider;

/** Detects Spring Boot on {@code compileClasspath} without resolving anything at configuration time. */
public final class SpringDetector {

    private SpringDetector() {}

    /** Lenient view so an unresolvable dependency never breaks detection; project components never match. */
    public static Provider<Boolean> detected(Project project) {
        return project.getConfigurations()
                .getByName("compileClasspath")
                .getIncoming()
                .artifactView(view -> view.setLenient(true))
                .getArtifacts()
                .getResolvedArtifacts()
                .map(artifacts -> artifacts.stream()
                        .map(a -> a.getId().getComponentIdentifier())
                        .anyMatch(id -> id instanceof ModuleComponentIdentifier m
                                && SpringArtifacts.isSpringBoot(m.getGroup(), m.getModule())));
    }
}
