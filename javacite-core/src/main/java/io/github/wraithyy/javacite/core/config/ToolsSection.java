package io.github.wraithyy.javacite.core.config;

import java.util.Map;

/** The parsed {@code tools:} block. Tools absent from {@code settings} are on. */
public record ToolsSection(
        Map<Tool, ToolSetting> settings,
        NullawayMode nullaway,
        double jacocoMin,
        DependencyCheckOptions dependencyCheck,
        SonarMode sonar) {

    public ToolsSection {
        settings = Map.copyOf(settings);
    }

    public static ToolsSection defaults() {
        return new ToolsSection(
                Map.of(), NullawayMode.defaults(), 0.80, DependencyCheckOptions.defaults(), SonarMode.AUTO);
    }
}
