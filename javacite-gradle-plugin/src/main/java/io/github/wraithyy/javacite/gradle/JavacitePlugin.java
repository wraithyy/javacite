package io.github.wraithyy.javacite.gradle;

import io.github.wraithyy.javacite.core.config.ConfigException;
import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.gradle.tasks.DoctorTask;
import io.github.wraithyy.javacite.gradle.tasks.InitTask;
import io.github.wraithyy.javacite.gradle.tasks.Probes;
import io.github.wraithyy.javacite.gradle.tasks.SonarProfileTask;
import io.github.wraithyy.javacite.gradle.wiring.BannedDepsWiring;
import io.github.wraithyy.javacite.gradle.wiring.CheckstyleWiring;
import io.github.wraithyy.javacite.gradle.wiring.DependencyCheckWiring;
import io.github.wraithyy.javacite.gradle.wiring.ErrorProneWiring;
import io.github.wraithyy.javacite.gradle.wiring.JacocoWiring;
import io.github.wraithyy.javacite.gradle.wiring.PmdWiring;
import io.github.wraithyy.javacite.gradle.wiring.SonarWiring;
import io.github.wraithyy.javacite.gradle.wiring.SpotbugsWiring;
import io.github.wraithyy.javacite.gradle.wiring.SpotlessWiring;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/** Entry point of the javacite Gradle plugin. */
public class JavacitePlugin implements Plugin<Project> {

    static final int REQUIRED_JDK = 21;

    @Override
    public void apply(Project project) {
        JavaciteExtension extension = project.getExtensions().create("javacite", JavaciteExtension.class);

        // fileContents registers the file as a configuration-cache input; a missing file reads as empty.
        Provider<JavaciteConfig> config = Probes.configText(project)
                .map(text -> text.isBlank() ? ConfigLoader.defaults() : ConfigLoader.load(text));
        extension.getConfigStorage().set(config);
        extension.getConfigStorage().disallowChanges();
        // Read eagerly: wiring below branches on the config at apply time, and a broken file should fail
        // with one clean message rather than from whichever consumer touches it first.
        try {
            config.get();
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof ConfigException) {
                    throw new GradleException("javacite.yml: " + t.getMessage(), t);
                }
            }
            throw e;
        }

        if (project == project.getRootProject()) {
            InitTask.register(project);
            DoctorTask.register(project);
            SonarProfileTask.register(project);
        } else {
            project.getLogger()
                    .info("javacite: javaciteInit, javaciteDoctor and javaciteSonarProfile are registered on the root"
                            + " project only; apply the plugin there and run them from the root.");
        }
        project.getPluginManager().withPlugin("java", ignored -> configure(project, extension, config));
    }

    private static void configure(Project project, JavaciteExtension extension, Provider<JavaciteConfig> config) {
        project.getTasks().withType(JavaCompile.class).configureEach(compile -> compile.getOptions()
                .getRelease()
                .set(config.map(JavaciteConfig::java)));

        project.getTasks().register("javaciteCheck", task -> {
            task.setGroup("verification");
            task.setDescription("Fast static checks: format, compile with Error Prone, Checkstyle, PMD, SpotBugs.");
        });
        project.getTasks().register("javaciteFix", task -> {
            task.setGroup("formatting");
            task.setDescription("Applies automatic fixes such as formatting.");
        });
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(check -> check.dependsOn("javaciteCheck"));
        BannedDepsWiring.apply(project, extension);
        DependencyCheckWiring.apply(project, extension);
        JacocoWiring.apply(project, extension);
        SonarWiring.apply(project, extension);
        SpotlessWiring.apply(project, extension);
        ErrorProneWiring.apply(project, extension);
        CheckstyleWiring.apply(project, extension);
        PmdWiring.apply(project, extension);
        SpotbugsWiring.apply(project, extension);

        // Toolchain is only final after the build script ran, hence afterEvaluate for the gate alone;
        // when another plugin applied java from its own afterEvaluate, evaluation is already over.
        if (project.getState().getExecuted()) {
            checkJdk(project);
        } else {
            project.afterEvaluate(JavacitePlugin::checkJdk);
        }
    }

    private static void checkJdk(Project project) {
        if (JavaVersion.current().compareTo(JavaVersion.toVersion(REQUIRED_JDK)) >= 0) {
            return;
        }
        JavaLanguageVersion toolchain = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getToolchain()
                .getLanguageVersion()
                .getOrNull();
        if (toolchain == null || toolchain.asInt() < REQUIRED_JDK) {
            throw new GradleException("javacite requires JDK " + REQUIRED_JDK + " or newer, but Gradle runs on JDK "
                    + JavaVersion.current().getMajorVersion()
                    + ". Run Gradle on JDK " + REQUIRED_JDK + ", or configure a toolchain: "
                    + "java { toolchain { languageVersion = JavaLanguageVersion.of(" + REQUIRED_JDK + ") } }");
        }
    }
}
