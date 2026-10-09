package io.github.wraithyy.javacite.maven.mojo;

import io.github.wraithyy.javacite.core.generate.BuildTool;
import io.github.wraithyy.javacite.core.init.InitWriter;
import io.github.wraithyy.javacite.maven.JavaciteSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Bootstraps a repo: javacite.yml, agent rule files, hooks, ArchitectureTest and {@code .mvn/extensions.xml}. */
@Mojo(name = "init", requiresProject = true, aggregator = true, threadSafe = true)
public class InitMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin.version}", readonly = true, required = true)
    private String pluginVersion;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Path root = JavaciteSupport.rootDir(session);
            Path configPath = JavaciteSupport.configPath(session);
            String text = Files.exists(configPath) ? Files.readString(configPath, StandardCharsets.UTF_8) : "";
            List<String> report = new ArrayList<>();
            report.add(extensions(root));
            report.addAll(InitWriter.run(
                    root,
                    project.getBasedir().toPath(),
                    configPath,
                    BuildTool.MAVEN,
                    text,
                    JavaciteSupport.springDetected(project),
                    List.of(),
                    true,
                    JavaciteSupport.archunitDeclared(project)));
            report.forEach(line -> getLog().info("javacite init: " + line));
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("javacite init failed: " + e.getMessage(), e);
        }
    }

    /** Creates the extension descriptor, or adds our entry to an existing one without touching the rest. */
    private String extensions(Path root) throws IOException {
        Path file = root.resolve(".mvn/extensions.xml");
        String entry = "  <extension>\n"
                + "    <groupId>io.github.wraithyy</groupId>\n"
                + "    <artifactId>javacite-maven-plugin</artifactId>\n"
                + "    <version>" + pluginVersion + "</version>\n"
                + "  </extension>\n";
        if (Files.notExists(file)) {
            return InitWriter.write(
                    file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<extensions>\n" + entry + "</extensions>\n", false, root);
        }
        String existing = Files.readString(file, StandardCharsets.UTF_8);
        if (existing.contains("javacite-maven-plugin")) {
            return "unchanged .mvn/extensions.xml";
        }
        int end = existing.lastIndexOf("</extensions>");
        if (end < 0) {
            return "skipped .mvn/extensions.xml (no </extensions> found, add javacite-maven-plugin manually)";
        }
        return InitWriter.write(file, existing.substring(0, end) + entry + existing.substring(end), false, root);
    }
}
