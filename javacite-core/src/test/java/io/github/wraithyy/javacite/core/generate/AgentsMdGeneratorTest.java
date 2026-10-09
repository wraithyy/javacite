package io.github.wraithyy.javacite.core.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentsMdGeneratorTest {

    private static final ResolvedRules RULES = ResolvedRules.of(ConfigLoader.defaults());

    @Test
    void rendersFixedStructure() {
        String block = AgentsMdGenerator.render(RULES, BuildTool.GRADLE, true);

        assertThat(block).startsWith(AgentsMdGenerator.START).endsWith(AgentsMdGenerator.END);
        assertThat(block).contains("# javacite code standards", "## Quick reference", "./gradlew javaciteCheck");
        assertThat(block).contains("## Core principles", "## What javacite cannot check", "## Spring");
        assertThat(block.indexOf("## Correctness")).isLessThan(block.indexOf("## Style"));
        assertThat(block.indexOf("## Style")).isLessThan(block.indexOf("## Architecture"));
        assertThat(AgentsMdGenerator.render(RULES, BuildTool.GRADLE, true)).isEqualTo(block);
    }

    @Test
    void mavenCommandsAndNoSpringSection() {
        String block = AgentsMdGenerator.render(RULES, BuildTool.MAVEN, false);

        assertThat(block).contains("mvn verify").doesNotContain("## Spring").doesNotContain("gradlew");
    }

    @Test
    void optedOutRuleDisappearsAndWarnIsSuffixed() {
        ResolvedRules r = ResolvedRules.of(ConfigLoader.load("""
                rules:
                  pmd.LocalVariableCouldBeFinal: off
                  pmd.AvoidCatchingThrowable: warn
                """));
        String block = AgentsMdGenerator.render(r, BuildTool.GRADLE, false);

        assertThat(block).doesNotContain("Declare local variables final when they are never reassigned.");
        assertThat(block).contains("- Never catch Throwable or Error; catch the specific types. (warning)");
    }

    @Test
    void mergeKeepsUserContentAboveAndBelowAndIsIdempotent() {
        String block = AgentsMdGenerator.render(RULES, BuildTool.GRADLE, false);
        String existing = "# My project\n\nHand written.\n\n" + AgentsMdGenerator.START + "\nOLD\n"
                + AgentsMdGenerator.END + "\n\n## Footer\nkeep me\n";

        String merged = AgentsMdGenerator.merge(existing, block);

        assertThat(merged).startsWith("# My project\n\nHand written.\n\n").endsWith("\n\n## Footer\nkeep me\n");
        assertThat(merged).doesNotContain("OLD");
        assertThat(AgentsMdGenerator.merge(merged, block)).isEqualTo(merged);
    }

    @Test
    void mergeAppendsOrCreates() {
        assertThat(AgentsMdGenerator.merge(null, "B")).isEqualTo("B\n");
        assertThat(AgentsMdGenerator.merge("# Hi\n", "B")).isEqualTo("# Hi\n\nB\n");
    }

    @Test
    void editorFilesFollowTargets() {
        String block = AgentsMdGenerator.render(RULES, BuildTool.GRADLE, false);
        Map<String, String> files = EditorRulesGenerator.render(
                block,
                List.of("agents-md", "claude-md", "cursor", "copilot", "windsurf"),
                p -> p.equals("AGENTS.md") ? Optional.of("# Mine\n") : Optional.empty());

        assertThat(files).containsOnlyKeys(
                "AGENTS.md", ".cursor/rules/javacite.mdc", ".github/copilot-instructions.md", ".windsurf/rules/javacite.md");
        assertThat(files.get("AGENTS.md")).startsWith("# Mine\n\n");
        assertThat(files.get(".cursor/rules/javacite.mdc"))
                .startsWith("---\ndescription: javacite code standards\nalwaysApply: true\n---\n\n");
        assertThat(EditorRulesGenerator.claudeMdStrategy()).isEqualTo(EditorRulesGenerator.ClaudeMdStrategy.SYMLINK);
        assertThat(EditorRulesGenerator.render(block, List.of("copilot"), p -> Optional.empty())).containsOnlyKeys(
                ".github/copilot-instructions.md");
    }

    @Test
    void danglingStartMarkerFailsInsteadOfAppending() {
        assertThatThrownBy(() -> AgentsMdGenerator.merge("# Hi\n" + AgentsMdGenerator.START + "\nstale\n", "B"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AgentsMdGenerator.END);
    }

    @Test
    void mergePreservesCrlf() {
        String block = "A\nB";
        String appended = AgentsMdGenerator.merge("# Hi\r\nuser\r\n", block);
        assertThat(appended).isEqualTo("# Hi\r\nuser\r\n\r\nA\r\nB\r\n");
        String replaced = AgentsMdGenerator.merge(
                "x\r\n" + AgentsMdGenerator.START + "\r\nold\r\n" + AgentsMdGenerator.END + "\r\ny\r\n",
                AgentsMdGenerator.START + "\nnew\n" + AgentsMdGenerator.END);
        assertThat(replaced.replace("\r\n", "")).doesNotContain("\n");
        assertThat(replaced).contains("new").doesNotContain("old");
    }
}
