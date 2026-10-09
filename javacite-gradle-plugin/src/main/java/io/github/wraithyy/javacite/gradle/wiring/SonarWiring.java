package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.sonar.SonarProperties;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.sonarqube.gradle.SonarExtension;

/** Applies the Sonar scanner plugin and points it at the report locations of the other tools. */
public final class SonarWiring {

    private SonarWiring() {}

    /** Prints the properties handed to the scanner; registered only when Sonar is wired. */
    @DisableCachingByDefault(because = "Only prints properties")
    public abstract static class SonarPropertiesTask extends DefaultTask {
        @Input
        public abstract MapProperty<String, String> getSonarProperties();

        @TaskAction
        void print() {
            getSonarProperties().get().forEach((k, v) -> getLogger().quiet(k + "=" + v));
        }
    }

    public static void apply(Project project, JavaciteExtension extension) {
        JavaciteConfig config = extension.getConfig().get();
        boolean active =
                switch (config.sonar()) {
                    case ON -> true;
                    case OFF -> false;
                    case AUTO -> project.getProviders().environmentVariable("SONAR_HOST_URL").isPresent()
                            || project.getProviders().environmentVariable("SONAR_TOKEN").isPresent();
                };
        if (!active) {
            return;
        }
        Map<String, String> props = new LinkedHashMap<>(SonarProperties.forGradle(
                project.getLayout().getBuildDirectory().get().getAsFile().getAbsolutePath()));
        props.put("sonar.java.source", String.valueOf(config.java()));

        project.getPluginManager().apply("org.sonarqube");
        project.getExtensions().getByType(SonarExtension.class).properties(sonar -> props.forEach(sonar::property));

        project.getTasks().register("javaciteSonarProperties", SonarPropertiesTask.class, task -> {
            task.setGroup("javacite");
            task.setDescription("Prints the Sonar properties javacite configures.");
            task.getSonarProperties().set(props);
        });
    }
}
