package io.github.wraithyy.javacite.archunit;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.domain.JavaClass;
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

    private JavaciteRules() {}

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
