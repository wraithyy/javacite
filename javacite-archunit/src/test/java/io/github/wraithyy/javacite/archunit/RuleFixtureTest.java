package io.github.wraithyy.javacite.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RuleFixtureTest {

    private static final String BASE = "io.github.wraithyy.javacite.archunit.fixtures.";
    private static final JavaciteRuleSwitches ON = JavaciteRuleSwitches.from(null);

    static Stream<Arguments> cases() {
        return Stream.of(
                c("fieldInjection", JavaciteRules.noFieldInjection(ON)),
                c("systemOut", JavaciteRules.noSystemOut(ON)),
                c("julLogging", JavaciteRules.noJavaUtilLogging(ON)),
                c("genericExceptions", JavaciteRules.noGenericExceptions(ON)),
                c("cycles", JavaciteRules.noPackageCycles(ON)),
                c("constructorInjection", JavaciteSpringRules.constructorInjectionOnly(ON)),
                c("layered", JavaciteSpringRules.layeredArchitecture(ON)),
                c("txController", JavaciteSpringRules.noTransactionalOnControllers(ON)),
                c("entityController", JavaciteSpringRules.noEntitiesInControllerSignatures(ON)),
                c("mockBean", JavaciteSpringRules.noMockBeanInUnitTests(ON)),
                c("serviceNaming", JavaciteSpringRules.serviceNaming(ON)),
                c("repositoryNaming", JavaciteSpringRules.repositoryNaming(ON)));
    }

    private static Arguments c(String name, ArchRule rule) {
        return Arguments.of(Named.of(name, name), rule);
    }

    @ParameterizedTest(name = "{0}: bad fixture violates")
    @MethodSource("cases")
    void badFixtureViolates(String name, ArchRule rule) {
        var classes = new ClassFileImporter().importPackages(BASE + "bad." + name);
        assertThat(rule.evaluate(classes).hasViolation()).isTrue();
    }

    @ParameterizedTest(name = "{0}: good fixture passes")
    @MethodSource("cases")
    void goodFixturePasses(String name, ArchRule rule) {
        var classes = new ClassFileImporter().importPackages(BASE + "good." + name);
        assertThat(classes).isNotEmpty();
        assertThat(rule.evaluate(classes).hasViolation()).isFalse();
    }

    @org.junit.jupiter.api.Test
    void flatSinglePackagePassesCycleRule() {
        var classes = new ClassFileImporter().importPackages(BASE + "good.flat");
        assertThat(classes).isNotEmpty();
        JavaciteRules.noPackageCycles(ON).check(classes);
    }
}
