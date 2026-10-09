package io.github.wraithyy.javacite.core.sonar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class SonarProjectPropertiesTest {

    @Test
    void sortsKeysAndEscapesSpecialCharacters() {
        String rendered = SonarProjectProperties.render(Map.of("b.key", "C:\\work\\x", "a.key", "one two"));

        assertThat(rendered).isEqualTo("a.key=one\\ two\nb.key=C\\:\\\\work\\\\x\n");
    }

    @Test
    void roundTripsThroughJavaProperties() throws IOException {
        Map<String, String> input = Map.of("k:1", "a=b#c\\d:e\nf", "plain", "x,y");
        Properties parsed = new Properties();

        parsed.load(new StringReader(SonarProjectProperties.render(input)));

        assertThat(parsed).containsExactlyInAnyOrderEntriesOf(input);
    }
}
