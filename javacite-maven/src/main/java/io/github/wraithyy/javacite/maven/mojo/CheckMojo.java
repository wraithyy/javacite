package io.github.wraithyy.javacite.maven.mojo;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.maven.JavaciteSupport;
import java.io.IOException;
import java.util.Arrays;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Alias that only reports; the checks themselves are plugin executions the extension injected into verify. */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            JavaciteConfig config = JavaciteSupport.loadConfig(session);
            String enabled = Arrays.stream(Tool.values())
                    .filter(t -> config.tool(t).enabled())
                    .map(Tool::key)
                    .toList()
                    .toString();
            getLog().info("javacite enabled tools: " + enabled);
            getLog().info("Checks run as part of the lifecycle: use 'mvn verify -DskipTests' for the fast gate.");
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("javacite: " + e.getMessage(), e);
        }
    }
}
