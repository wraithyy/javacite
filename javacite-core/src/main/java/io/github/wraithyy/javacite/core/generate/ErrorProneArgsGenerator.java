package io.github.wraithyy.javacite.core.generate;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.NullawayMode;
import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.rules.ResolvedRule;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the Error Prone / NullAway compiler arguments (without the {@code -Xplugin:ErrorProne} prefix). */
public final class ErrorProneArgsGenerator {

    public List<String> args(ResolvedRules rules, JavaciteConfig config) {
        List<String> out = new ArrayList<>();
        boolean nullaway = false;
        for (ResolvedRule r : rules.all()) {
            Tool t = r.rule().tool();
            if (t != Tool.ERRORPRONE && t != Tool.NULLAWAY) {
                continue;
            }
            out.add("-Xep:" + r.rule().nativeKey() + ":" + r.level().name().toUpperCase(Locale.ROOT));
            nullaway |= t == Tool.NULLAWAY && r.level() != RuleLevel.OFF;
        }
        if (nullaway) {
            NullawayMode mode = config.nullaway();
            out.add(
                    mode.kind() == NullawayMode.Kind.ANNOTATED_PACKAGES
                            ? "-XepOpt:NullAway:AnnotatedPackages=" + String.join(",", mode.packages())
                            : "-XepOpt:NullAway:OnlyNullMarked=true");
        }
        out.add("-XepDisableWarningsInGeneratedCode");
        out.add("-XepExcludedPaths:.*/build/generated/.*");
        return List.copyOf(out);
    }
}
