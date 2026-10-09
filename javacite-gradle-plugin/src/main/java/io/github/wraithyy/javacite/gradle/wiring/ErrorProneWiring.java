package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.generate.ErrorProneArgsGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import io.github.wraithyy.javacite.gradle.JavaciteVersions;
import java.util.List;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

/** Error Prone and NullAway on every compile task, configured from the registry. */
public final class ErrorProneWiring {

    // Needed by Error Prone on JDK 21 so type annotations on symbols are visible to checks.
    private static final String ADD_TYPE_ANNOTATIONS = "-XDaddTypeAnnotationsToSymbol=true";

    private ErrorProneWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        JavaciteConfig config = extension.getConfig().get();
        if (!config.tool(Tool.ERRORPRONE).enabled()) {
            return;
        }
        project.getPluginManager().apply("net.ltgt.errorprone");
        project.getDependencies().add("errorprone", "com.google.errorprone:error_prone_core:" + JavaciteVersions.ERRORPRONE_CORE);
        if (config.tool(Tool.NULLAWAY).enabled()) {
            project.getDependencies().add("errorprone", "com.uber.nullaway:nullaway:" + JavaciteVersions.NULLAWAY);
        }
        String jspecify = "org.jspecify:jspecify:" + JavaciteVersions.JSPECIFY;
        project.getDependencies().add("compileOnly", jspecify);
        project.getDependencies().add("testCompileOnly", jspecify);

        Provider<List<String>> args = extension.getConfig()
                .map(c -> new ErrorProneArgsGenerator().args(ResolvedRules.of(c), c));
        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().getCompilerArgs().add(ADD_TYPE_ANNOTATIONS);
            ErrorProneOptions ep = ((ExtensionAware) compile.getOptions()).getExtensions().getByType(ErrorProneOptions.class);
            ep.getDisableWarningsInGeneratedCode().set(true);
            ep.getErrorproneArgs().addAll(args);
        });
        project.getTasks().named("javaciteCheck").configure(t -> t.dependsOn("compileJava", "compileTestJava"));
    }
}
