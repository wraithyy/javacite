package io.github.wraithyy.javacite.core.rules;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry merged with javacite.yml: the single view generators read. */
public final class ResolvedRules {

    private static final String SPRING_PREFIX = "archunit.spring.";

    private final Map<String, ResolvedRule> byId;

    private ResolvedRules(Map<String, ResolvedRule> byId) {
        this.byId = byId;
    }

    /** Treats {@code spring: auto} as detected; use the overload when detection is known. */
    public static ResolvedRules of(JavaciteConfig config) {
        return of(config, true);
    }

    public static ResolvedRules of(JavaciteConfig config, boolean springDetected) {
        boolean spring =
                switch (config.spring()) {
                    case ON -> true;
                    case OFF -> false;
                    case AUTO -> springDetected;
                };
        Map<String, ResolvedRule> out = new LinkedHashMap<>();
        for (Rule r : RuleRegistry.load().rules()) {
            RuleLevel level = config.rules().getOrDefault(r.id(), r.defaultLevel());
            if (!config.tool(r.tool()).enabled() || (!spring && r.id().startsWith(SPRING_PREFIX))) {
                level = RuleLevel.OFF;
            }
            out.put(r.id(), new ResolvedRule(r, level));
        }
        return new ResolvedRules(out);
    }

    public List<ResolvedRule> all() {
        return List.copyOf(byId.values());
    }

    public List<ResolvedRule> enabledFor(Tool tool) {
        return byId.values().stream()
                .filter(r -> r.rule().tool() == tool && r.level() != RuleLevel.OFF)
                .toList();
    }

    public RuleLevel level(String id) {
        ResolvedRule r = byId.get(id);
        if (r == null) {
            throw new IllegalArgumentException("Unknown rule id: " + id);
        }
        return r.level();
    }

    public boolean isEnabled(String id) {
        return level(id) != RuleLevel.OFF;
    }
}
