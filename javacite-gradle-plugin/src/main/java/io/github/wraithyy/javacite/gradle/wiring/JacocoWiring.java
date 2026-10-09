package io.github.wraithyy.javacite.gradle.wiring;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.gradle.JavaciteExtension;
import io.github.wraithyy.javacite.gradle.JavaciteVersions;
import java.math.BigDecimal;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;

/** JaCoCo line-coverage gate: XML report for Sonar, verification wired into {@code check}. */
public final class JacocoWiring {

    private JacocoWiring() {}

    public static void apply(Project project, JavaciteExtension extension) {
        JavaciteConfig config = extension.getConfig().get();
        if (!config.tool(Tool.JACOCO).enabled()) {
            return;
        }
        project.getPluginManager().apply("jacoco");
        project.getExtensions().getByType(JacocoPluginExtension.class).setToolVersion(JavaciteVersions.JACOCO);

        project.getTasks().named("jacocoTestReport", JacocoReport.class).configure(report -> {
            report.getReports().getXml().getRequired().set(true);
            report.getReports()
                    .getXml()
                    .getOutputLocation()
                    .set(project.getLayout().getBuildDirectory().file("reports/jacoco/test/jacocoTestReport.xml"));
        });

        project.getTasks().named("jacocoTestCoverageVerification", JacocoCoverageVerification.class)
                .configure(verification -> verification.getViolationRules().rule(rule -> rule.limit(limit -> {
                    limit.setCounter("LINE");
                    limit.setValue("COVEREDRATIO");
                    limit.setMinimum(BigDecimal.valueOf(config.jacocoMin()));
                })));

        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(check -> check.dependsOn("jacocoTestCoverageVerification"));
        project.getTasks().withType(Test.class).named("test").configure(test -> test.finalizedBy("jacocoTestReport"));
    }
}
