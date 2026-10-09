package io.github.wraithyy.javacite.core.generate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import io.github.wraithyy.javacite.core.sonar.SonarProperties;
import org.junit.jupiter.api.Test;

class SonarProfileGeneratorTest {

    @Test
    void emitsRulesWithSonarKeysAndPriorities() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.load("""
                rules:
                  checkstyle.IllegalCatch: warn
                """));
        String xml = SonarProfileGenerator.generate(r, "javacite & co");

        assertThat(xml).startsWith("<profile>").contains("<name>javacite &amp; co</name>", "<language>java</language>");
        assertThat(xml).contains("<repositoryKey>java</repositoryKey>\n      <key>S1181</key>\n      <priority>MINOR</priority>");
        assertThat(xml).contains("<key>S1117</key>\n      <priority>MAJOR</priority>");
        assertThat(xml).doesNotContain("java:S");
    }

    @Test
    void disabledSonarRulesAreOmitted() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.load("""
                rules:
                  checkstyle.HiddenField: off
                """));

        assertThat(SonarProfileGenerator.generate(r, "p")).doesNotContain("<key>S1117</key>");
    }

    @Test
    void profileWithoutAnySonarKeysHasEmptyRules() {
        StringBuilder sb = new StringBuilder("tools:\n  checkstyle: off\n  pmd: off\n  spotbugs: off\n  errorprone: off\n  nullaway: off\n  archunit: off\n");
        String xml = SonarProfileGenerator.generate(ResolvedRules.of(ConfigLoader.load(sb.toString())), "p");

        assertThat(xml).doesNotContain("<rule>").contains("<rules>");
    }

    @Test
    void propertiesUseConventionalLocations() {
        assertThat(SonarProperties.forGradle("build"))
                .containsEntry("sonar.java.checkstyle.reportPaths", "build/reports/checkstyle/main.xml")
                .containsEntry("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
                .hasSize(4);
        assertThat(SonarProperties.forMaven("target"))
                .containsEntry("sonar.java.spotbugs.reportPaths", "target/spotbugsXml.xml")
                .containsEntry("sonar.coverage.jacoco.xmlReportPaths", "target/site/jacoco/jacoco.xml");
    }
}
