package io.github.wraithyy.javacite.maven;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.generate.CheckstyleXmlGenerator;
import io.github.wraithyy.javacite.core.generate.PmdRulesetGenerator;
import io.github.wraithyy.javacite.core.generate.SpotbugsFilterGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import io.github.wraithyy.javacite.core.spring.SpringArtifacts;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

/** Config loading, Spring detection and generated-file output shared by the extension and the mojos. */
public final class JavaciteSupport {

    private JavaciteSupport() {}

    private static final String VERSION_RESOURCE = "javacite-maven.properties";

    /** Version of this plugin, from a resource written by Maven resource filtering; never guessed. */
    public static String selfVersion() {
        try (InputStream in = JavaciteSupport.class.getResourceAsStream("/" + VERSION_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("javacite: bundled " + VERSION_RESOURCE + " is missing; broken plugin build");
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            if (version == null || version.isBlank() || version.contains("${")) {
                throw new IllegalStateException("javacite: " + VERSION_RESOURCE + " carries no filtered version: " + version);
            }
            return version;
        } catch (IOException e) {
            throw new IllegalStateException("javacite: cannot read " + VERSION_RESOURCE, e);
        }
    }

    public static Path rootDir(MavenSession session) {
        File root = session.getRequest().getMultiModuleProjectDirectory();
        return (root != null ? root : new File(session.getExecutionRootDirectory())).toPath();
    }

    public static Path configPath(MavenSession session) {
        return rootDir(session).resolve("javacite.yml");
    }

    public static JavaciteConfig loadConfig(MavenSession session) throws IOException {
        Path yml = configPath(session);
        return Files.exists(yml)
                ? ConfigLoader.load(Files.readString(yml, StandardCharsets.UTF_8))
                : ConfigLoader.defaults();
    }

    public static boolean appliesTo(MavenProject project) {
        String packaging = project.getPackaging();
        return "jar".equals(packaging) || "war".equals(packaging);
    }

    public static boolean isSpring(JavaciteConfig config, MavenProject project) {
        return switch (config.spring()) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> springDetected(project);
        };
    }

    public static boolean springDetected(MavenProject project) {
        return project.getDependencies().stream().anyMatch(JavaciteSupport::isSpringBoot);
    }

    /** Declared dependencies only: init/doctor run without dependency resolution. */
    public static boolean archunitDeclared(MavenProject project) {
        return project.getDependencies().stream().anyMatch(d -> "javacite-archunit".equals(d.getArtifactId()));
    }

    // The core rule is SpringArtifacts.isSpringBoot (spring-boot exactly). Maven only sees spring-boot transitively
    // after dependency resolution, which has not happened yet here, so for declared dependencies the starters
    // (spring-boot-starter*) stand in for it.
    private static boolean isSpringBoot(Dependency d) {
        return SpringArtifacts.isSpringBoot(d.getGroupId(), d.getArtifactId())
                || ("org.springframework.boot".equals(d.getGroupId())
                        && d.getArtifactId().startsWith("spring-boot-starter"));
    }

    /** Written at build time (not in afterProjectsRead) so that {@code mvn clean verify} does not delete them. */
    public static void writeConfigs(JavaciteConfig config, MavenProject project) throws IOException {
        ResolvedRules rules = ResolvedRules.of(config, isSpring(config, project));
        Path dir = Path.of(project.getBuild().getDirectory(), "javacite");
        Files.createDirectories(dir);
        if (config.tool(Tool.CHECKSTYLE).enabled()) {
            Files.writeString(dir.resolve("checkstyle.xml"), new CheckstyleXmlGenerator().generate(rules));
        }
        if (config.tool(Tool.PMD).enabled()) {
            Files.writeString(dir.resolve("pmd.xml"), new PmdRulesetGenerator().generate(rules));
        }
        if (config.tool(Tool.SPOTBUGS).enabled()) {
            Files.writeString(dir.resolve("spotbugs-exclude.xml"), new SpotbugsFilterGenerator().generate(rules));
        }
    }
}
