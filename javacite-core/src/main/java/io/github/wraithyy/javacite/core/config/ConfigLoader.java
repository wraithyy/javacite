package io.github.wraithyy.javacite.core.config;

import static io.github.wraithyy.javacite.core.config.YamlNodes.checkKeys;
import static io.github.wraithyy.javacite.core.config.YamlNodes.enumValue;
import static io.github.wraithyy.javacite.core.config.YamlNodes.integer;
import static io.github.wraithyy.javacite.core.config.YamlNodes.map;
import static io.github.wraithyy.javacite.core.config.YamlNodes.strings;

import io.github.wraithyy.javacite.core.rules.RuleRegistry;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/** Parses and validates javacite.yml. */
public final class ConfigLoader {

    private static final List<String> TOP_KEYS = List.of("version", "java", "tools", "spring", "rules", "deps", "agents");
    private static final List<String> AGENT_TARGETS =
            List.of("agents-md", "claude-md", "cursor", "copilot", "windsurf", "claude-hooks", "git-hooks");
    private static final int MIN_JAVA = 17;
    private static final int MAX_SUGGESTIONS = 5;

    private static final java.util.regex.Pattern BAN_ENTRY = java.util.regex.Pattern.compile("^[^:\\s]+:[^:\\s]+$");

    private ConfigLoader() {}

    public static JavaciteConfig defaults() {
        return new JavaciteConfig(
                MIN_JAVA,
                ToolsSection.defaults(),
                SpringMode.AUTO,
                Map.of(),
                List.of("commons-lang:commons-lang", "log4j:log4j"),
                AGENT_TARGETS);
    }

    public static JavaciteConfig load(String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        Object root;
        try {
            root = new Load(LoadSettings.builder().build()).loadFromString(yaml);
        } catch (YamlEngineException e) {
            throw new ConfigException("Invalid YAML: " + e.getMessage(), e);
        }
        if (root == null) {
            return defaults();
        }
        Map<String, Object> top = map(root, "<root>");
        checkKeys(top, TOP_KEYS, "<root>");

        JavaciteConfig d = defaults();
        checkVersion(top);
        return new JavaciteConfig(
                top.containsKey("java") ? java(top.get("java")) : d.java(),
                top.containsKey("tools") ? ToolsParser.parse(top.get("tools")) : d.tools(),
                top.containsKey("spring")
                        ? enumValue(SpringMode.class, m -> m.name().toLowerCase(Locale.ROOT), top.get("spring"), "spring")
                        : d.spring(),
                top.containsKey("rules") ? rules(top.get("rules")) : d.rules(),
                top.containsKey("deps") ? deps(top.get("deps"), d) : d.bannedDeps(),
                top.containsKey("agents") ? agents(top.get("agents"), d) : d.agentTargets());
    }

    private static void checkVersion(Map<String, Object> top) {
        if (top.containsKey("version") && !Integer.valueOf(1).equals(top.get("version"))) {
            throw new ConfigException("Unsupported 'version': " + top.get("version") + ". Only version 1 is supported");
        }
    }

    private static int java(Object node) {
        int java = integer(node, "java");
        if (java < MIN_JAVA) {
            throw new ConfigException("Value of 'java' must be " + MIN_JAVA + " or higher but was " + java);
        }
        return java;
    }

    private static Map<String, RuleLevel> rules(Object node) {
        Set<String> known = RuleRegistry.load().ids();
        Map<String, RuleLevel> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(node, "rules").entrySet()) {
            if (!known.contains(e.getKey())) {
                throw new ConfigException("Unknown rule id '" + e.getKey() + "'. Closest valid ids: " + closest(e.getKey(), known));
            }
            out.put(e.getKey(), enumValue(RuleLevel.class, l -> l.name().toLowerCase(Locale.ROOT), e.getValue(), "rules." + e.getKey()));
        }
        return out;
    }

    /** Ranks ids by shared leading characters; good enough for typos at the end of an id. */
    private static List<String> closest(String id, Set<String> known) {
        String lower = id.toLowerCase(Locale.ROOT);
        return known.stream()
                .sorted(Comparator.comparingInt((String k) -> -commonPrefix(lower, k.toLowerCase(Locale.ROOT)))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static List<String> deps(Object node, JavaciteConfig d) {
        Map<String, Object> deps = map(node, "deps");
        checkKeys(deps, List.of("ban"), "deps");
        if (!deps.containsKey("ban")) {
            return d.bannedDeps();
        }
        List<String> ban = strings(deps.get("ban"), "deps.ban");
        for (String entry : ban) {
            if (!BAN_ENTRY.matcher(entry).matches()) {
                throw new ConfigException("Invalid entry '" + entry + "' in 'deps.ban': expected 'group:name'."
                        + " Gradle matches group and name exactly, so wildcards, versions and extra segments do not work");
            }
        }
        return ban;
    }

    private static List<String> agents(Object node, JavaciteConfig d) {
        Map<String, Object> agents = map(node, "agents");
        checkKeys(agents, List.of("targets"), "agents");
        if (!agents.containsKey("targets")) {
            return d.agentTargets();
        }
        List<String> targets = strings(agents.get("targets"), "agents.targets");
        for (String t : targets) {
            if (!AGENT_TARGETS.contains(t)) {
                throw new ConfigException("Unknown agent target '" + t + "' at 'agents.targets'. Valid targets: " + AGENT_TARGETS);
            }
        }
        return targets;
    }
}
