package io.github.wraithyy.javacite.maven.mojo;

import io.github.wraithyy.javacite.maven.JavaciteSupport;
import java.io.IOException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Writes target/javacite/pmd.xml; the extension binds it to validate. */
@Mojo(name = "generate-configs", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class GenerateConfigsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            JavaciteSupport.writeConfigs(JavaciteSupport.loadConfig(session), project);
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("javacite: cannot generate tool configs: " + e.getMessage(), e);
        }
    }
}
