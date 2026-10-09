package io.github.wraithyy.javacite.core.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wraithyy.javacite.core.rules.RuleRegistry;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RulesDocGeneratorTest {

    private final String doc = RulesDocGenerator.generate();

    @Test
    void everyRuleAppearsExactlyOnce() {
        RuleRegistry.load().rules().forEach(r -> {
            String row = "| `" + r.id() + "` |";
            assertThat(doc.split(Pattern.quote(row), -1)).as(r.id()).hasSize(2);
        });
    }

    @Test
    void marksItselfAsGenerated() {
        assertThat(doc).startsWith("<!-- GENERATED").contains("Do not edit");
    }

    @Test
    void tableRowsHaveFiveColumns() {
        assertThat(doc.lines().filter(l -> l.startsWith("| `")))
                .isNotEmpty()
                .allSatisfy(l -> assertThat(l.replace("\\|", "").chars().filter(c -> c == '|').count())
                        .isEqualTo(6));
    }

    @Test
    void isDeterministic() {
        assertThat(RulesDocGenerator.generate()).isEqualTo(doc);
    }
}
