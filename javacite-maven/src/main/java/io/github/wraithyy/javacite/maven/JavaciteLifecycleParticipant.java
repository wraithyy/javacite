package io.github.wraithyy.javacite.maven;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import java.io.IOException;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/** Core extension: mutates every jar/war project model before the lifecycle is planned. */
public class JavaciteLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        JavaciteConfig config;
        try {
            config = JavaciteSupport.loadConfig(session);
        } catch (IOException | RuntimeException e) {
            throw new MavenExecutionException("javacite: invalid javacite.yml: " + e.getMessage(), e);
        }
        String version;
        try {
            version = JavaciteSupport.selfVersion();
        } catch (IllegalStateException e) {
            throw new MavenExecutionException(e.getMessage(), e);
        }
        ExecutionInjector injector = new ExecutionInjector(config, System.getenv(), version);
        for (MavenProject project : session.getProjects()) {
            if (JavaciteSupport.appliesTo(project)) {
                injector.inject(project, JavaciteSupport.isSpring(config, project));
            }
        }
    }
}
