package io.github.wraithyy.javacite.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RuleRegistryTest {

    private final List<Rule> rules = RuleRegistry.load().rules();

    @Test
    void registryIsSubstantial() {
        Map<Tool, Long> perTool = rules.stream().collect(Collectors.groupingBy(Rule::tool, Collectors.counting()));
        assertThat(perTool.get(Tool.CHECKSTYLE)).isGreaterThanOrEqualTo(55);
        assertThat(perTool.get(Tool.PMD)).isGreaterThanOrEqualTo(40);
        assertThat(perTool.get(Tool.ERRORPRONE)).isGreaterThanOrEqualTo(20);
        assertThat(perTool.get(Tool.NULLAWAY)).isEqualTo(1);
        assertThat(perTool.get(Tool.SPOTBUGS)).isGreaterThanOrEqualTo(5);
        assertThat(perTool.get(Tool.ARCHUNIT)).isEqualTo(12);
    }

    @Test
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Rule r : rules) {
            assertThat(seen.add(r.id())).as("duplicate id " + r.id()).isTrue();
        }
    }

    @Test
    void everyFieldIsPopulated() {
        for (Rule r : rules) {
            assertThat(r.id()).as("id").isNotBlank();
            assertThat(r.tool()).as(r.id() + " tool").isNotNull();
            assertThat(r.nativeKey()).as(r.id() + " native").isNotBlank();
            assertThat(r.defaultLevel()).as(r.id() + " default").isNotNull();
            assertThat(r.category()).as(r.id() + " category").isNotBlank();
            assertThat(r.agentText()).as(r.id() + " agent").isNotBlank().endsWith(".");
            assertThat(r.sonarKeys()).as(r.id() + " sonar").doesNotContainNull();
        }
    }

    @Test
    void idNamespaceMatchesTool() {
        for (Rule r : rules) {
            String ns = r.id().substring(0, r.id().indexOf('.'));
            String expected = r.tool() == Tool.NULLAWAY ? "errorprone" : r.tool().key();
            assertThat(ns).as(r.id()).isEqualTo(expected);
        }
    }

    @Test
    void nativeKeysFollowToolConventions() {
        for (Rule r : rules) {
            switch (r.tool()) {
                case CHECKSTYLE -> assertThat(r.nativeKey())
                        .as(r.id())
                        .startsWith("com.puppycrawl.tools.checkstyle.checks.")
                        .endsWith("Check");
                case PMD -> assertThat(r.nativeKey()).as(r.id()).matches("category/java/[a-z]+\\.xml/[A-Za-z]+");
                case ERRORPRONE, NULLAWAY -> assertThat(r.nativeKey()).as(r.id()).matches("[A-Z][A-Za-z]+");
                case SPOTBUGS -> assertThat(r.nativeKey()).as(r.id()).matches("[A-Z_]+");
                case ARCHUNIT -> assertThat(r.nativeKey()).as(r.id()).matches("[A-Z_]+");
                default -> throw new AssertionError("tool without rules: " + r.tool());
            }
            String simple = r.id().substring(r.id().lastIndexOf('.') + 1);
            if (r.tool() == Tool.CHECKSTYLE) {
                assertThat(r.nativeKey()).as(r.id()).endsWith("." + simple + "Check");
            } else if (r.tool() == Tool.PMD) {
                assertThat(r.nativeKey()).as(r.id()).endsWith("/" + simple);
            } else if (r.tool() == Tool.ERRORPRONE || r.tool() == Tool.NULLAWAY) {
                assertThat(r.nativeKey()).as(r.id()).isEqualTo(simple);
            }
        }
    }

    @Test
    void sonarKeysAreUniqueAndWellFormed() {
        Set<String> seen = new HashSet<>();
        for (Rule r : rules) {
            for (String key : r.sonarKeys()) {
                assertThat(key).as(r.id()).matches("java:S\\d+");
                assertThat(seen.add(key)).as("sonar key " + key + " duplicated at " + r.id()).isTrue();
            }
        }
        assertThat(seen).contains("java:S106", "java:S1192", "java:S2259", "java:S1181");
    }

    @Test
    void requiredRulesPresentWithExpectedMapping() {
        Map<String, Rule> byId = rules.stream().collect(Collectors.toMap(Rule::id, Function.identity()));
        assertThat(byId).containsKeys(
                "checkstyle.FinalLocalVariable", "checkstyle.FinalParameters", "checkstyle.MagicNumber",
                "checkstyle.MissingJavadocMethod", "checkstyle.AvoidStarImport", "checkstyle.UnusedImports",
                "checkstyle.NeedBraces", "checkstyle.EqualsHashCode", "checkstyle.HiddenField",
                "checkstyle.IllegalCatch", "checkstyle.IllegalThrows", "checkstyle.MultipleVariableDeclarations",
                "checkstyle.ParameterAssignment", "checkstyle.SimplifyBooleanExpression",
                "checkstyle.StringLiteralEquality", "checkstyle.VisibilityModifier",
                "errorprone.NullAway", "errorprone.MissingOverride", "errorprone.UnusedVariable",
                "archunit.NoFieldInjection", "archunit.NoSystemOut", "archunit.NoJavaUtilLogging",
                "archunit.NoGenericExceptions", "archunit.NoPackageCycles",
                "archunit.spring.ConstructorInjectionOnly", "archunit.spring.LayeredArchitecture",
                "archunit.spring.NoTransactionalOnControllers", "archunit.spring.NoEntitiesInControllerSignatures",
                "archunit.spring.NoMockBeanInUnitTests", "archunit.spring.ServiceNaming",
                "archunit.spring.RepositoryNaming");
        assertThat(byId.get("errorprone.NullAway").tool()).isEqualTo(Tool.NULLAWAY);
        assertThat(byId.get("checkstyle.FinalLocalVariable").nativeKey())
                .isEqualTo("com.puppycrawl.tools.checkstyle.checks.coding.FinalLocalVariableCheck");
        assertThat(byId.get("spotbugs.STYLE").defaultLevel()).isEqualTo(RuleLevel.WARN);
        assertThat(byId.get("spotbugs.CORRECTNESS").defaultLevel()).isEqualTo(RuleLevel.ERROR);
    }

    @Test
    void excludedNoisyPmdRulesAbsent() {
        assertThat(rules).extracting(Rule::id)
                .doesNotContain("pmd.LawOfDemeter", "pmd.OnlyOneReturn", "pmd.AtLeastOneConstructor", "pmd.CommentRequired");
    }

    @Test
    void loadIsCached() {
        assertThat(RuleRegistry.load()).isSameAs(RuleRegistry.load());
    }
}
