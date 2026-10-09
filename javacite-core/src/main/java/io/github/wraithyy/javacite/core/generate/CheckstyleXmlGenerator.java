package io.github.wraithyy.javacite.core.generate;

import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.rules.ResolvedRule;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders the enabled Checkstyle rules as a Checkstyle 14 configuration. */
public final class CheckstyleXmlGenerator {

    // Checks that must sit directly under Checker (they are FileSetChecks, not AST checks).
    private static final Set<String> CHECKER_LEVEL = Set.of(
            "FileTabCharacter", "NewlineAtEndOfFile", "RegexpSingleline", "RegexpMultiline", "JavadocPackage",
            "Translation", "UniqueProperties", "OrderedProperties", "RegexpOnFilename", "Header", "RegexpHeader");

    private static final String HEADER =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE module PUBLIC "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN" "https://checkstyle.org/dtds/configuration_1_3.dtd">
            """;

    public String generate(ResolvedRules rules) {
        List<ResolvedRule> enabled = rules.enabledFor(Tool.CHECKSTYLE);
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("<module name=\"Checker\">\n");
        sb.append("  <module name=\"SuppressWarningsFilter\"/>\n");
        for (ResolvedRule r : enabled) {
            if (CHECKER_LEVEL.contains(simpleName(r))) {
                module(sb, "  ", r);
            }
        }
        sb.append("  <module name=\"TreeWalker\">\n");
        sb.append("    <module name=\"SuppressWarningsHolder\"/>\n");
        // A TreeWalker child, not a Checker child, because it reads comments from the file contents.
        sb.append("    <module name=\"SuppressionCommentFilter\"/>\n");
        for (ResolvedRule r : enabled) {
            if (!CHECKER_LEVEL.contains(simpleName(r))) {
                module(sb, "    ", r);
            }
        }
        sb.append("  </module>\n</module>\n");
        return sb.toString();
    }

    /** Check name as used in Checkstyle config: class simple name without the Check suffix. */
    static String simpleName(ResolvedRule r) {
        String fqcn = r.rule().nativeKey();
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return simple.endsWith("Check") ? simple.substring(0, simple.length() - "Check".length()) : simple;
    }

    private static void module(StringBuilder sb, String indent, ResolvedRule r) {
        String name = XmlEscape.attr(simpleName(r));
        Map<String, String> props = new LinkedHashMap<>(r.rule().properties());
        if (r.level() == RuleLevel.WARN) {
            props.put("severity", "warning");
        }
        if (props.isEmpty()) {
            sb.append(indent).append("<module name=\"").append(name).append("\"/>\n");
            return;
        }
        sb.append(indent).append("<module name=\"").append(name).append("\">\n");
        props.forEach((k, v) -> sb.append(indent)
                .append("  <property name=\"")
                .append(XmlEscape.attr(k))
                .append("\" value=\"")
                .append(XmlEscape.attr(v))
                .append("\"/>\n"));
        sb.append(indent).append("</module>\n");
    }
}
