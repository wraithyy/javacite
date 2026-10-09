package io.github.wraithyy.javacite.core.generate;

import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.rules.ResolvedRule;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Renders the javacite block of AGENTS.md and merges it into an existing file between marker comments. */
public final class AgentsMdGenerator {

    public static final String START = "<!-- javacite:start -->";
    public static final String END = "<!-- javacite:end -->";

    private static final String SPRING_PREFIX = "archunit.spring.";
    // "null-safety" is the registry's name for the nullness category.
    private static final List<String> CATEGORY_ORDER = List.of(
            "nullness",
            "null-safety",
            "correctness",
            "design",
            "style",
            "architecture",
            "security",
            "performance",
            "testing");

    private AgentsMdGenerator() {}

    public static String render(ResolvedRules rules, BuildTool buildTool, boolean spring) {
        StringBuilder sb = new StringBuilder();
        sb.append(START).append("\n# javacite code standards\n\n");
        sb.append("## Quick reference\n\n");
        sb.append("- Check: `").append(buildTool.checkCommand()).append("`\n");
        sb.append("- Fix (format): `").append(buildTool.fixCommand()).append("`\n");
        sb.append("- Doctor: `").append(buildTool.doctorCommand()).append("`\n\n");
        sb.append("## Core principles\n\n");
        sb.append("- Write code that passes the javacite checks on the first run.\n");
        sb.append("- Prefer simple, explicit code over clever abstractions.\n");
        sb.append("- Handle every error path; never swallow exceptions.\n");
        sb.append("- Keep methods small and classes focused on one responsibility.\n");
        sb.append("- Do not suppress a rule to make a check pass; fix the cause or ask first.\n\n");

        Map<String, Map<String, Boolean>> byCategory = new LinkedHashMap<>();
        Map<String, Boolean> springRules = new LinkedHashMap<>();
        for (ResolvedRule r : rules.all()) {
            String text = r.rule().agentText();
            if (r.level() == RuleLevel.OFF || text == null || text.isBlank()) {
                continue;
            }
            boolean springRule = r.rule().id().startsWith(SPRING_PREFIX);
            if (springRule && !spring) {
                continue;
            }
            Map<String, Boolean> target =
                    springRule ? springRules : byCategory.computeIfAbsent(r.rule().category(), k -> new LinkedHashMap<>());
            // An identical text is a warning only if every rule carrying it is a warning.
            target.merge(text.strip(), r.level() == RuleLevel.WARN, (a, b) -> a && b);
        }

        for (String category : orderedCategories(byCategory.keySet())) {
            section(sb, title(category), byCategory.get(category));
        }
        if (spring && !springRules.isEmpty()) {
            section(sb, "Spring", springRules);
        }

        sb.append("## What javacite cannot check\n\n");
        sb.append("Review these yourself; no tool verifies them:\n\n");
        sb.append("- Business logic correctness\n");
        sb.append("- Whether names express intent\n");
        sb.append("- Architecture beyond layering\n");
        sb.append("- Edge cases and failure scenarios\n");
        sb.append("- User experience\n");
        sb.append("- Documentation accuracy\n\n");
        sb.append("Before finishing, run `").append(buildTool.fixCommand()).append("` and then `")
                .append(buildTool.checkCommand()).append("`.\n");
        sb.append(END);
        return sb.toString();
    }

    /**
     * Replaces an existing marker block in place, otherwise appends the block after a blank line. Keeps CRLF when the
     * existing file uses it. A start marker without a matching end marker is an error: appending a second block would
     * leave the dangling one to swallow user text on the next run.
     */
    public static String merge(String existing, String block) {
        String base = existing == null ? "" : existing;
        boolean crlf = base.contains("\r\n");
        String nl = crlf ? "\r\n" : "\n";
        String text = crlf ? block.replace("\r\n", "\n").replace("\n", "\r\n") : block;
        int start = base.indexOf(START);
        int end = start < 0 ? -1 : base.indexOf(END, start);
        if (start >= 0 && end < 0) {
            throw new IllegalArgumentException(
                    "Found '" + START + "' without a matching '" + END + "'; fix or remove the dangling marker and re-run");
        }
        if (start >= 0) {
            return base.substring(0, start) + text + base.substring(end + END.length());
        }
        String trimmed = base.stripTrailing();
        return trimmed.isEmpty() ? text + nl : trimmed + nl + nl + text + nl;
    }

    private static void section(StringBuilder sb, String title, Map<String, Boolean> items) {
        sb.append("## ").append(title).append("\n\n");
        items.forEach((text, warn) -> sb.append("- ").append(text).append(warn ? " (warning)" : "").append('\n'));
        sb.append('\n');
    }

    private static List<String> orderedCategories(Iterable<String> present) {
        TreeSet<String> rest = new TreeSet<>();
        present.forEach(rest::add);
        List<String> out = new ArrayList<>();
        for (String c : CATEGORY_ORDER) {
            if (rest.remove(c)) {
                out.add(c);
            }
        }
        out.addAll(rest);
        return out;
    }

    private static String title(String category) {
        String spaced = category.replace('-', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
