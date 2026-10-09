package io.github.wraithyy.javacite.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void roundTripKeepsKeyOrderAndValues() {
        String src = "{\"b\": [1, 2.5, true, null, \"x\\n\\\"q\\\"\"], \"a\": {}, \"c\": []}";
        String printed = Json.print(Json.parse(src));

        assertThat(printed.indexOf("\"b\"")).isLessThan(printed.indexOf("\"a\""));
        assertThat(Json.print(Json.parse(printed))).isEqualTo(printed);
        assertThat(printed).contains("2.5").contains("null").contains("\\n").contains("\\\"q\\\"");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Json.parse("{\"a\": }")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.parse("{} x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capsNestingDepth() {
        assertThat(Json.parse("[".repeat(64) + "]".repeat(64))).isNotNull();
        assertThatThrownBy(() -> Json.parse("[".repeat(65) + "]".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nesting");
        assertThatThrownBy(() -> Json.parse("[".repeat(100000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesUnicodeEscapesAndWrapsBadOnes() {
        assertThat(Json.parse("\"\\u00e9\"")).isEqualTo("\u00e9");
        assertThatThrownBy(() -> Json.parse("\"\\uZZZZ\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.parse("\"\\u12\"")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedNumbers() {
        assertThatThrownBy(() -> Json.parse("-")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.parse("+1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Json.parse("-1")).isEqualTo(new java.math.BigDecimal("-1"));
    }
}
