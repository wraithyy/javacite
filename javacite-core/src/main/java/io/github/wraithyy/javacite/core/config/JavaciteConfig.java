package io.github.wraithyy.javacite.core.config;

import java.util.List;
import java.util.Map;

/** Immutable, validated view of javacite.yml merged over defaults. */
public record JavaciteConfig(
        int java,
        ToolsSection tools,
        SpringMode spring,
        Map<String, RuleLevel> rules,
        List<String> bannedDeps,
        List<String> agentTargets) {

    public JavaciteConfig {
        rules = Map.copyOf(rules);
        bannedDeps = List.copyOf(bannedDeps);
        agentTargets = List.copyOf(agentTargets);
    }

    public ToolSetting tool(Tool tool) {
        return tools.settings().getOrDefault(tool, new ToolSetting.On());
    }

    public NullawayMode nullaway() {
        return tools.nullaway();
    }

    public double jacocoMin() {
        return tools.jacocoMin();
    }

    public DependencyCheckOptions dependencyCheck() {
        return tools.dependencyCheck();
    }

    public SonarMode sonar() {
        return tools.sonar();
    }
}
