package io.github.wraithyy.javacite.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.function.Function;

/**
 * Wraps a rule so it evaluates to "no violations" when switched off in javacite.yml. The delegate is built
 * per evaluation because some rules (package cycles) depend on the imported classes.
 */
final class GatedRule implements ArchRule {

    private final String id;
    private final JavaciteRuleSwitches switches;
    private final Function<JavaClasses, ArchRule> factory;
    private final Function<ArchRule, ArchRule> decorator;
    private final String description;

    GatedRule(String id, JavaciteRuleSwitches switches, String description, Function<JavaClasses, ArchRule> factory) {
        this(id, switches, description, factory, Function.identity());
    }

    private GatedRule(
            String id,
            JavaciteRuleSwitches switches,
            String description,
            Function<JavaClasses, ArchRule> factory,
            Function<ArchRule, ArchRule> decorator) {
        this.id = id;
        this.switches = switches;
        this.description = description;
        this.factory = factory;
        this.decorator = decorator;
    }

    static GatedRule of(String id, JavaciteRuleSwitches switches, ArchRule rule) {
        // Projects legitimately have no matching classes (e.g. no controllers); that is not a failure.
        ArchRule lenient = rule.allowEmptyShould(true);
        return new GatedRule(id, switches, rule.getDescription(), c -> lenient);
    }

    private GatedRule decorated(String newDescription, Function<ArchRule, ArchRule> next) {
        return new GatedRule(id, switches, newDescription, factory, decorator.andThen(next));
    }

    @Override
    public void check(JavaClasses classes) {
        if (switches.isEnabled(id)) {
            decorator.apply(factory.apply(classes)).check(classes);
        }
    }

    @Override
    public EvaluationResult evaluate(JavaClasses classes) {
        if (!switches.isEnabled(id)) {
            return new EvaluationResult(this, com.tngtech.archunit.lang.Priority.MEDIUM);
        }
        return decorator.apply(factory.apply(classes)).evaluate(classes);
    }

    @Override
    public ArchRule because(String reason) {
        return decorated(description + ", because " + reason, r -> r.because(reason));
    }

    @Override
    public ArchRule allowEmptyShould(boolean allowEmptyShould) {
        return decorated(description, r -> r.allowEmptyShould(allowEmptyShould));
    }

    @Override
    public ArchRule as(String newDescription) {
        return decorated(newDescription, r -> r.as(newDescription));
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
