package io.github.wraithyy.javacite.core.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import org.junit.jupiter.api.Test;

class ResolvedRulesTest {

    @Test
    void defaultsMatchRegistryDefaults() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.defaults());

        assertThat(r.level("checkstyle.FinalLocalVariable")).isEqualTo(RuleLevel.ERROR);
        assertThat(r.level("spotbugs.STYLE")).isEqualTo(RuleLevel.WARN);
        assertThat(r.isEnabled("pmd.CloseResource")).isTrue();
        assertThat(r.all()).hasSize(RuleRegistry.load().rules().size());
    }

    @Test
    void overridesApply() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.load("""
                rules:
                  checkstyle.FinalLocalVariable: off
                  pmd.CloseResource: warn
                  spotbugs.STYLE: error
                """));

        assertThat(r.level("checkstyle.FinalLocalVariable")).isEqualTo(RuleLevel.OFF);
        assertThat(r.isEnabled("checkstyle.FinalLocalVariable")).isFalse();
        assertThat(r.level("pmd.CloseResource")).isEqualTo(RuleLevel.WARN);
        assertThat(r.isEnabled("pmd.CloseResource")).isTrue();
        assertThat(r.level("spotbugs.STYLE")).isEqualTo(RuleLevel.ERROR);
        assertThat(r.level("checkstyle.MagicNumber")).isEqualTo(RuleLevel.ERROR);
    }

    @Test
    void enabledForExcludesOffRulesAndKeepsWarn() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.load("""
                rules:
                  checkstyle.FinalLocalVariable: off
                  checkstyle.MagicNumber: warn
                """));

        assertThat(r.enabledFor(Tool.CHECKSTYLE)).extracting(x -> x.rule().id())
                .doesNotContain("checkstyle.FinalLocalVariable")
                .contains("checkstyle.MagicNumber");
        assertThat(r.enabledFor(Tool.CHECKSTYLE)).allMatch(x -> x.rule().tool() == Tool.CHECKSTYLE);
        assertThat(r.enabledFor(Tool.CHECKSTYLE).stream()
                        .filter(x -> x.rule().id().equals("checkstyle.MagicNumber"))
                        .findFirst().orElseThrow().level())
                .isEqualTo(RuleLevel.WARN);
    }

    @Test
    void toolOffDisablesAllItsRules() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.load("tools:\n  pmd: off\n  nullaway: off"));

        assertThat(r.enabledFor(Tool.PMD)).isEmpty();
        assertThat(r.level("pmd.CloseResource")).isEqualTo(RuleLevel.OFF);
        assertThat(r.isEnabled("errorprone.NullAway")).isFalse();
        assertThat(r.enabledFor(Tool.CHECKSTYLE)).isNotEmpty();
        assertThat(r.isEnabled("errorprone.MissingOverride")).isTrue();
    }

    @Test
    void springOffDropsSpringRulesAndDetectionControlsAuto() {
        ResolvedRules off = ResolvedRules.of(ConfigLoader.load("spring: off"));
        assertThat(off.isEnabled("archunit.spring.LayeredArchitecture")).isFalse();
        assertThat(off.isEnabled("archunit.NoFieldInjection")).isTrue();

        assertThat(ResolvedRules.of(ConfigLoader.defaults(), false).isEnabled("archunit.spring.ServiceNaming")).isFalse();
        assertThat(ResolvedRules.of(ConfigLoader.defaults(), true).isEnabled("archunit.spring.ServiceNaming")).isTrue();
        assertThat(ResolvedRules.of(ConfigLoader.load("spring: on"), false).isEnabled("archunit.spring.ServiceNaming")).isTrue();
        assertThat(ResolvedRules.of(ConfigLoader.defaults()).isEnabled("archunit.spring.ServiceNaming")).isTrue();
    }

    @Test
    void unknownIdThrows() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.defaults());
        assertThatThrownBy(() -> r.level("nope.X")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope.X");
        assertThatThrownBy(() -> r.isEnabled("nope.X")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultIsImmutable() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.defaults());
        assertThatThrownBy(() -> r.all().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
