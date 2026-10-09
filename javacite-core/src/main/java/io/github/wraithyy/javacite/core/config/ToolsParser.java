package io.github.wraithyy.javacite.core.config;

import static io.github.wraithyy.javacite.core.config.YamlNodes.checkKeys;
import static io.github.wraithyy.javacite.core.config.YamlNodes.enumValue;
import static io.github.wraithyy.javacite.core.config.YamlNodes.map;
import static io.github.wraithyy.javacite.core.config.YamlNodes.number;
import static io.github.wraithyy.javacite.core.config.YamlNodes.string;
import static io.github.wraithyy.javacite.core.config.YamlNodes.strings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the {@code tools:} block. */
final class ToolsParser {

    private static final String SONAR = "sonar";
    private static final String PATH = "tools";

    private ToolsParser() {}

    static ToolsSection parse(Object node) {
        Map<String, Object> tools = map(node, PATH);
        List<String> validKeys = new ArrayList<>(java.util.Arrays.stream(Tool.values()).map(Tool::key).toList());
        validKeys.add(SONAR);
        checkKeys(tools, validKeys, PATH);

        ToolsSection d = ToolsSection.defaults();
        Map<Tool, ToolSetting> settings = new EnumMap<>(Tool.class);
        NullawayMode nullaway = d.nullaway();
        double jacocoMin = d.jacocoMin();
        DependencyCheckOptions dep = d.dependencyCheck();
        SonarMode sonar = d.sonar();

        for (Map.Entry<String, Object> e : tools.entrySet()) {
            String path = PATH + "." + e.getKey();
            if (e.getKey().equals(SONAR)) {
                sonar = enumValue(SonarMode.class, m -> m.name().toLowerCase(Locale.ROOT), e.getValue(), path);
                continue;
            }
            Tool tool = Tool.fromKey(e.getKey()).orElseThrow();
            settings.put(tool, setting(tool, e.getValue(), path));
            if (e.getValue() instanceof Map<?, ?>) {
                Map<String, Object> o = map(e.getValue(), path);
                switch (tool) {
                    case NULLAWAY -> nullaway = nullaway(o, path);
                    case JACOCO -> jacocoMin = jacocoMin(o, path, jacocoMin);
                    case DEPENDENCY_CHECK -> dep = dependencyCheck(o, path);
                    default -> {
                        // validated in setting(); no typed view
                    }
                }
            }
        }
        // Only an explicit nullaway setting fails: leaving it out while errorprone is off just means no NullAway.
        if (settings.get(Tool.NULLAWAY) != null
                && settings.get(Tool.NULLAWAY).enabled()
                && settings.get(Tool.ERRORPRONE) instanceof ToolSetting.Off) {
            throw new ConfigException(
                    "Tool 'nullaway' at 'tools.nullaway' requires 'errorprone': NullAway runs inside Error Prone,"
                            + " so enable tools.errorprone or switch tools.nullaway off");
        }
        return new ToolsSection(settings, nullaway, jacocoMin, dep, sonar);
    }

    private static ToolSetting setting(Tool tool, Object value, String path) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> options = map(value, path);
            List<String> valid = validOptionKeys(tool);
            if (valid.isEmpty()) {
                throw new ConfigException("Tool '" + tool.key() + "' does not accept options (at '" + path + "'); use on or off");
            }
            checkKeys(options, valid, path);
            options.forEach((k, v) -> {
                if (v == null) {
                    throw new ConfigException("Option '" + path + "." + k + "' has no value");
                }
            });
            if (tool == Tool.SPOTLESS && options.containsKey("ratchetFrom")) {
                string(options.get("ratchetFrom"), path + ".ratchetFrom");
            }
            return new ToolSetting.Options(options);
        }
        return switch (enumValue(Switch.class, s -> s.name().toLowerCase(Locale.ROOT), value, path)) {
            case ON -> new ToolSetting.On();
            case OFF -> new ToolSetting.Off();
        };
    }

    private static List<String> validOptionKeys(Tool tool) {
        return switch (tool) {
            case NULLAWAY -> List.of("mode", "packages");
            case JACOCO -> List.of("min");
            case DEPENDENCY_CHECK -> List.of("failOnCvss", "inCheck");
            case SPOTLESS -> List.of("ratchetFrom");
            default -> List.of();
        };
    }

    private static NullawayMode nullaway(Map<String, Object> o, String path) {
        NullawayMode.Kind kind = o.containsKey("mode")
                ? enumValue(NullawayMode.Kind.class, NullawayMode.Kind::key, o.get("mode"), path + ".mode")
                : NullawayMode.Kind.ONLY_NULL_MARKED;
        List<String> packages = o.containsKey("packages") ? strings(o.get("packages"), path + ".packages") : List.of();
        if (kind == NullawayMode.Kind.ANNOTATED_PACKAGES && packages.isEmpty()) {
            throw new ConfigException("Mode 'annotatedPackages' at '" + path + "' requires a non-empty 'packages' list");
        }
        return new NullawayMode(kind, packages);
    }

    private static double jacocoMin(Map<String, Object> o, String path, double fallback) {
        if (!o.containsKey("min")) {
            return fallback;
        }
        double min = number(o.get("min"), path + ".min");
        if (min < 0 || min > 1) {
            throw new ConfigException("Value of '" + path + ".min' must be between 0 and 1 but was " + min);
        }
        return min;
    }

    private static DependencyCheckOptions dependencyCheck(Map<String, Object> o, String path) {
        DependencyCheckOptions d = DependencyCheckOptions.defaults();
        double cvss = o.containsKey("failOnCvss") ? number(o.get("failOnCvss"), path + ".failOnCvss") : d.failOnCvss();
        if (cvss < 0 || cvss > 10) {
            throw new ConfigException("Value of '" + path + ".failOnCvss' must be between 0 and 10 but was " + cvss);
        }
        DependencyCheckOptions.InCheck inCheck = o.containsKey("inCheck")
                ? enumValue(
                        DependencyCheckOptions.InCheck.class,
                        m -> m.name().toLowerCase(Locale.ROOT),
                        o.get("inCheck"),
                        path + ".inCheck")
                : d.inCheck();
        return new DependencyCheckOptions(cvss, inCheck);
    }

    private enum Switch {
        ON,
        OFF
    }
}
