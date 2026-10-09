package io.github.wraithyy.javacite.core.sonar;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sonar analysis properties pointing at the conventional report locations of each build tool. */
public final class SonarProperties {

    private SonarProperties() {}

    /** @param buildDir project build directory, normally {@code build} */
    public static Map<String, String> forGradle(String buildDir) {
        return props(
                buildDir + "/reports/pmd/main.xml",
                buildDir + "/reports/jacoco/test/jacocoTestReport.xml");
    }

    /** @param targetDir project output directory, normally {@code target} */
    public static Map<String, String> forMaven(String targetDir) {
        return props(
                targetDir + "/pmd.xml",
                targetDir + "/site/jacoco/jacoco.xml");
    }

    private static Map<String, String> props(String pmd, String jacoco) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sonar.java.pmd.reportPaths", pmd);
        m.put("sonar.coverage.jacoco.xmlReportPaths", jacoco);
        return m;
    }
}
