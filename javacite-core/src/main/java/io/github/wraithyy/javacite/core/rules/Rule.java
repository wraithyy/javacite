package io.github.wraithyy.javacite.core.rules;

import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One registry entry; nativeKey is the tool's own identifier for the rule. Properties are passed
 * through to the tool config verbatim (PMD rule properties).
 */
public record Rule(
        String id,
        Tool tool,
        String nativeKey,
        RuleLevel defaultLevel,
        String category,
        List<String> sonarKeys,
        String agentText,
        Map<String, String> properties) {

    public Rule {
        sonarKeys = List.copyOf(sonarKeys);
        // Keep insertion order so generated configs are deterministic.
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
