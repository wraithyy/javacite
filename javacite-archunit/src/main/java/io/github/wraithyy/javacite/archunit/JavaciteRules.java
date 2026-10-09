package io.github.wraithyy.javacite.archunit;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/** General-purpose rules; include with {@code ArchTests.in(JavaciteRules.class)}. */
public final class JavaciteRules {

    @ArchTest
    public static final ArchRule NO_FIELD_INJECTION = noFieldInjection(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_SYSTEM_OUT = noSystemOut(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_JAVA_UTIL_LOGGING = noJavaUtilLogging(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_GENERIC_EXCEPTIONS = noGenericExceptions(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_PACKAGE_CYCLES = noPackageCycles(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule FIELDS_MUST_BE_PRIVATE = fieldsMustBePrivate(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_CLONE_OVERRIDE = noCloneOverride(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule IMMUTABLE_EXCEPTIONS = immutableExceptions(JavaciteRuleSwitches.current());

    private JavaciteRules() {}

    static ArchRule fieldsMustBePrivate(JavaciteRuleSwitches s) {
        // Synthetic fields (this$0, $VALUES, $assertionsDisabled) are compiler-made and not user-controllable.
        DescribedPredicate<JavaField> mutableState = DescribedPredicate.describe(
                "that are not static final",
                f -> !f.getModifiers().contains(JavaModifier.SYNTHETIC)
                        && !(f.getModifiers().contains(JavaModifier.STATIC)
                                && f.getModifiers().contains(JavaModifier.FINAL)));
        return GatedRule.of(
                "archunit.FieldsMustBePrivate",
                s,
                fields().that(mutableState).should().bePrivate().allowEmptyShould(true));
    }

    static ArchRule noCloneOverride(JavaciteRuleSwitches s) {
        return GatedRule.of(
                "archunit.NoCloneOverride",
                s,
                noMethods()
                        .that()
                        .haveName("clone")
                        .and()
                        .haveRawParameterTypes(new Class<?>[0])
                        // Condition is deliberately always true for the selected methods: any match is a violation.
                        .should()
                        .haveName("clone")
                        .as("no class should declare a parameterless clone() method")
                        .allowEmptyShould(true));
    }

    static ArchRule immutableExceptions(JavaciteRuleSwitches s) {
        return GatedRule.of(
                "archunit.ImmutableExceptions",
                s,
                fields().that()
                        .areDeclaredInClassesThat()
                        .areAssignableTo(Throwable.class)
                        .should()
                        .beFinal()
                        .allowEmptyShould(true));
    }

    static ArchRule noFieldInjection(JavaciteRuleSwitches s) {
        return GatedRule.of("archunit.NoFieldInjection", s, NO_CLASSES_SHOULD_USE_FIELD_INJECTION);
    }

    static ArchRule noSystemOut(JavaciteRuleSwitches s) {
        return GatedRule.of("archunit.NoSystemOut", s, NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS);
    }

    static ArchRule noJavaUtilLogging(JavaciteRuleSwitches s) {
        return GatedRule.of("archunit.NoJavaUtilLogging", s, NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING);
    }

    static ArchRule noGenericExceptions(JavaciteRuleSwitches s) {
        return GatedRule.of("archunit.NoGenericExceptions", s, NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS);
    }

    static ArchRule noPackageCycles(JavaciteRuleSwitches s) {
        return new GatedRule(
                "archunit.NoPackageCycles",
                s,
                "top-level packages below the common base package should be free of cycles",
                c -> SlicesRuleDefinition.slices()
                        .assignedFrom(topLevelSlices(commonPackage(c)))
                        .should()
                        .beFreeOfCycles()
                        // A flat single-package project has no slices; that is not a failure.
                        .allowEmptyShould(true));
    }

    /** Longest shared package prefix, segment-wise; the analysed application's base package. */
    private static String commonPackage(JavaClasses classes) {
        String[] common = null;
        for (JavaClass c : classes) {
            String[] parts = c.getPackageName().isEmpty() ? new String[0] : c.getPackageName().split("\\.");
            if (common == null) {
                common = parts;
                continue;
            }
            int n = 0;
            while (n < common.length && n < parts.length && common[n].equals(parts[n])) {
                n++;
            }
            common = java.util.Arrays.copyOf(common, n);
        }
        return common == null ? "" : String.join(".", common);
    }

    private static SliceAssignment topLevelSlices(String base) {
        String prefix = base.isEmpty() ? "" : base + ".";
        return new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass c) {
                String pkg = c.getPackageName();
                if (!pkg.startsWith(prefix) || pkg.length() == prefix.length() && !prefix.isEmpty()) {
                    return SliceIdentifier.ignore();
                }
                String rest = pkg.substring(prefix.length());
                if (rest.isEmpty()) {
                    return SliceIdentifier.ignore();
                }
                int dot = rest.indexOf('.');
                return SliceIdentifier.of(dot < 0 ? rest : rest.substring(0, dot));
            }

            @Override
            public String getDescription() {
                return "top-level package below " + (base.isEmpty() ? "(root)" : base);
            }
        };
    }
}
