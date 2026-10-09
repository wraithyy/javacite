package io.github.wraithyy.javacite.maven.mojo;

import io.github.wraithyy.javacite.core.generate.SonarProfileGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import io.github.wraithyy.javacite.maven.JavaciteSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Writes the SonarQube quality profile backup that mirrors the enabled javacite rules. */
@Mojo(name = "sonar-profile", threadSafe = true)
public class SonarProfileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            var config = JavaciteSupport.loadConfig(session);
            Path out = Path.of(project.getBuild().getDirectory(), "javacite", "sonar-profile.xml");
            Files.createDirectories(out.getParent());
            Files.writeString(
                    out,
                    SonarProfileGenerator.generate(
                            ResolvedRules.of(config, JavaciteSupport.isSpring(config, project)), "javacite"));
            getLog().info("javacite: wrote " + out);
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("javacite: cannot write sonar profile: " + e.getMessage(), e);
        }
    }
}
