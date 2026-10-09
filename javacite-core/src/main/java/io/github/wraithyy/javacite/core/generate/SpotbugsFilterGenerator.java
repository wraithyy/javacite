package io.github.wraithyy.javacite.core.generate;

import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.rules.ResolvedRule;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.List;

/**
 * Renders a SpotBugs exclude filter. SpotBugs has no warn level, so categories that are {@code off} or
 * {@code warn} are both excluded; only {@code error} categories are reported (and fail the build).
 */
public final class SpotbugsFilterGenerator {

    public String generate(ResolvedRules rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<FindBugsFilter>\n");
        for (ResolvedRule r : rules.all()) {
            if (r.rule().tool() == Tool.SPOTBUGS && r.level() != RuleLevel.ERROR) {
                sb.append("  <Match><Bug category=\"")
                        .append(XmlEscape.attr(r.rule().nativeKey()))
                        .append("\"/></Match>\n");
            }
        }
        return sb.append("</FindBugsFilter>\n").toString();
    }

    /** Categories that stay enabled (level error), in registry order. */
    public List<String> includeCategories(ResolvedRules rules) {
        return rules.all().stream()
                .filter(r -> r.rule().tool() == Tool.SPOTBUGS && r.level() == RuleLevel.ERROR)
                .map(r -> r.rule().nativeKey())
                .toList();
    }
}
