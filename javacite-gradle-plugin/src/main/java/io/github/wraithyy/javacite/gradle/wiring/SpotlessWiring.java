package io.github.wraithyy.javacite.gradle.wiring;

import com.diffplug.gradle.spotless.SpotlessExtension;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.config.ToolSetting;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import io.github.wraithyy.javacite.gradle.JavaciteVersions;
import org.gradle.api.Project;

/** Palantir formatting via Spotless; {@code -PspotlessIdeHook=<file>} is handled natively by Spotless. */
public final class SpotlessWiring {

    private SpotlessWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        JavaciteConfig config = extension.getConfig().get();
        ToolSetting setting = config.tool(Tool.SPOTLESS);
        if (!setting.enabled()) {
            return;
        }
        project.getPluginManager().apply("com.diffplug.spotless");
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        if (setting instanceof ToolSetting.Options options && options.values().get("ratchetFrom") instanceof String ref) {
            spotless.setRatchetFrom(ref);
        }
        spotless.java(java -> {
            java.palantirJavaFormat(JavaciteVersions.PALANTIR_JAVA_FORMAT);
            java.removeUnusedImports();
            java.trimTrailingWhitespace();
            java.endWithNewline();
            java.formatAnnotations();
        });
        project.getTasks().named("javaciteCheck").configure(t -> t.dependsOn("spotlessCheck"));
        project.getTasks().named("javaciteFix").configure(t -> t.dependsOn("spotlessApply"));
    }
}
