package io.github.wraithyy.javacite.core.generate;

import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.rules.ResolvedRule;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exports the enabled rules that carry sonar keys as a SonarQube quality profile backup XML.
 *
 * <p>Validated against SonarQube Community Build 26.9 via {@code scripts/sonar-validate.sh} (restore reported
 * 30 rule successes, 0 failures). Re-run the script when changing this format or targeting another server version.
 */
public final class SonarProfileGenerator {

    private SonarProfileGenerator() {}

    public static String generate(ResolvedRules rules, String profileName) {
        // key -> priority; error wins when two registry rules map to the same sonar rule.
        Map<String, String> entries = new LinkedHashMap<>();
        for (ResolvedRule r : rules.all()) {
            if (r.level() == RuleLevel.OFF) {
                continue;
            }
            String priority = r.level() == RuleLevel.ERROR ? "MAJOR" : "MINOR";
            for (String key : r.rule().sonarKeys()) {
                entries.merge(key, priority, (a, b) -> "MAJOR".equals(a) ? a : b);
            }
        }
        StringBuilder sb = new StringBuilder("<profile>\n");
        sb.append("  <name>").append(XmlEscape.attr(profileName)).append("</name>\n");
        sb.append("  <language>java</language>\n  <rules>\n");
        entries.forEach((full, priority) -> {
            int colon = full.indexOf(':');
            String repo = colon < 0 ? "java" : full.substring(0, colon);
            String key = colon < 0 ? full : full.substring(colon + 1);
            sb.append("    <rule>\n")
                    .append("      <repositoryKey>").append(XmlEscape.attr(repo)).append("</repositoryKey>\n")
                    .append("      <key>").append(XmlEscape.attr(key)).append("</key>\n")
                    .append("      <priority>").append(priority).append("</priority>\n")
                    .append("    </rule>\n");
        });
        return sb.append("  </rules>\n</profile>\n").toString();
    }
}
