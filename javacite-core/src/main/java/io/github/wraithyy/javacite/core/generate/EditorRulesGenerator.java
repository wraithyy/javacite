package io.github.wraithyy.javacite.core.generate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Fans the AGENTS block out to the per-editor rule files selected by {@code agents.targets}. */
public final class EditorRulesGenerator {

    /** How CLAUDE.md is provided; the caller creates the symlink to AGENTS.md. */
    public enum ClaudeMdStrategy {
        SYMLINK
    }

    private static final String CURSOR_FRONTMATTER =
            "---\ndescription: javacite code standards\nalwaysApply: true\n---";

    private EditorRulesGenerator() {}

    public static ClaudeMdStrategy claudeMdStrategy() {
        return ClaudeMdStrategy.SYMLINK;
    }

    /**
     * Returns repo-relative path to full file content. CLAUDE.md is never included (see {@link #claudeMdStrategy()});
     * requesting it implies AGENTS.md so the symlink target exists.
     */
    public static Map<String, String> render(
            String block, List<String> targets, Function<String, Optional<String>> existingReader) {
        Map<String, String> out = new LinkedHashMap<>();
        if (targets.contains("agents-md") || targets.contains("claude-md")) {
            put(out, "AGENTS.md", "", block, existingReader);
        }
        if (targets.contains("cursor")) {
            put(out, ".cursor/rules/javacite.mdc", CURSOR_FRONTMATTER, block, existingReader);
        }
        if (targets.contains("copilot")) {
            put(out, ".github/copilot-instructions.md", "", block, existingReader);
        }
        if (targets.contains("windsurf")) {
            put(out, ".windsurf/rules/javacite.md", "", block, existingReader);
        }
        return out;
    }

    private static void put(
            Map<String, String> out,
            String path,
            String freshBase,
            String block,
            Function<String, Optional<String>> existingReader) {
        out.put(path, AgentsMdGenerator.merge(existingReader.apply(path).orElse(freshBase), block));
    }
}
