package io.github.wraithyy.javacite.maven.mojo;

import io.github.wraithyy.javacite.core.sonar.SonarProjectProperties;
import io.github.wraithyy.javacite.core.sonar.SonarProperties;
import io.github.wraithyy.javacite.maven.JavaciteSupport;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Writes {@code target/javacite/sonar-project.properties} for the whole reactor so that a stock {@code sonar-scanner}
 * can be pointed at it. No scanner is run and no Sonar plugin is involved.
 */
@Mojo(
        name = "sonar-properties",
        aggregator = true,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class SonarPropertiesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            var config = JavaciteSupport.loadConfig(session);
            MavenProject top = session.getTopLevelProject();
            Path base = top.getBasedir().toPath().toAbsolutePath().normalize();

            Set<String> sources = new LinkedHashSet<>();
            Set<String> tests = new LinkedHashSet<>();
            Set<String> binaries = new LinkedHashSet<>();
            Set<String> testBinaries = new LinkedHashSet<>();
            Set<String> libraries = new LinkedHashSet<>();
            Set<String> testLibraries = new LinkedHashSet<>();
            Set<String> pmd = new LinkedHashSet<>();
            Set<String> jacoco = new LinkedHashSet<>();
            Set<String> junit = new LinkedHashSet<>();

            for (MavenProject p : session.getProjects()) {
                if (!JavaciteSupport.appliesTo(p)) {
                    continue;
                }
                Path moduleDir = p.getBasedir().toPath().toAbsolutePath().normalize();
                addIfDir(sources, base, moduleDir.resolve("src/main/java"));
                addIfDir(tests, base, moduleDir.resolve("src/test/java"));
                Path target = Path.of(p.getBuild().getDirectory()).toAbsolutePath().normalize();
                binaries.add(rel(base, Path.of(p.getBuild().getOutputDirectory())));
                testBinaries.add(rel(base, Path.of(p.getBuild().getTestOutputDirectory())));
                junit.add(rel(base, target.resolve("surefire-reports")));
                Map<String, String> reports = SonarProperties.forMaven(rel(base, target));
                pmd.add(reports.get("sonar.java.pmd.reportPaths"));
                jacoco.add(reports.get("sonar.coverage.jacoco.xmlReportPaths"));
                for (Artifact a : p.getArtifacts()) {
                    File f = a.getFile();
                    if (f == null) {
                        continue;
                    }
                    String path = rel(base, f.toPath());
                    testLibraries.add(path);
                    if (!Artifact.SCOPE_TEST.equals(a.getScope())) {
                        libraries.add(path);
                    }
                }
            }

            Map<String, String> props = new TreeMap<>();
            props.put("sonar.projectKey", top.getGroupId() + ":" + top.getArtifactId());
            props.put("sonar.projectBaseDir", base.toString());
            props.put("sonar.sources", String.join(",", sources));
            props.put("sonar.tests", String.join(",", tests));
            props.put("sonar.java.binaries", String.join(",", binaries));
            props.put("sonar.java.test.binaries", String.join(",", testBinaries));
            props.put("sonar.java.libraries", String.join(",", libraries));
            props.put("sonar.java.test.libraries", String.join(",", testLibraries));
            props.put("sonar.java.source", String.valueOf(config.java()));
            props.put("sonar.java.pmd.reportPaths", String.join(",", pmd));
            props.put("sonar.coverage.jacoco.xmlReportPaths", String.join(",", jacoco));
            props.put("sonar.junit.reportPaths", String.join(",", junit));

            Path out = Path.of(top.getBuild().getDirectory(), "javacite", "sonar-project.properties");
            Files.createDirectories(out.getParent());
            Files.writeString(out, SonarProjectProperties.render(props), StandardCharsets.UTF_8);
            getLog().info("javacite: wrote " + out);
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("javacite: cannot write sonar-project.properties: " + e.getMessage(), e);
        }
    }

    private static void addIfDir(Set<String> into, Path base, Path dir) {
        if (Files.isDirectory(dir)) {
            into.add(rel(base, dir));
        }
    }

    private static String rel(Path base, Path p) {
        Path abs = p.toAbsolutePath().normalize();
        return abs.startsWith(base) ? base.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }
}
