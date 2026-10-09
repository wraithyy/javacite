package io.github.wraithyy.javacite.core.rules;

import static io.github.wraithyy.javacite.core.config.YamlNodes.checkKeys;
import static io.github.wraithyy.javacite.core.config.YamlNodes.enumValue;
import static io.github.wraithyy.javacite.core.config.YamlNodes.map;
import static io.github.wraithyy.javacite.core.config.YamlNodes.string;
import static io.github.wraithyy.javacite.core.config.YamlNodes.strings;

import io.github.wraithyy.javacite.core.config.ConfigException;
import io.github.wraithyy.javacite.core.config.RuleLevel;
import io.github.wraithyy.javacite.core.config.Tool;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** The bundled rule catalogue, loaded from {@code javacite/rules-registry.yml}. */
public record RuleRegistry(List<Rule> rules) {

    private static final String RESOURCE = "/javacite/rules-registry.yml";
    private static final List<String> KEYS = List.of("id", "tool", "native", "default", "category", "sonar", "agent", "properties");

    public RuleRegistry {
        rules = List.copyOf(rules);
    }

    /** Initialization-on-demand holder: the registry is immutable, so one parse serves every caller. */
    private static final class Holder {
        static final RuleRegistry INSTANCE = read();
    }

    public static RuleRegistry load() {
        return Holder.INSTANCE;
    }

    private static RuleRegistry read() {
        try (InputStream in = RuleRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new ConfigException("Missing resource " + RESOURCE);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parse(new Load(LoadSettings.builder().build()).loadFromReader(reader));
            }
        } catch (IOException e) {
            throw new ConfigException("Cannot read " + RESOURCE, e);
        }
    }

    public Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>();
        rules.forEach(r -> ids.add(r.id()));
        return ids;
    }

    private static RuleRegistry parse(Object root) {
        if (!(root instanceof List<?> items)) {
            throw new ConfigException("Registry root must be a list");
        }
        List<Rule> rules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Rule rule = rule(map(items.get(i), "registry[" + i + "]"), "registry[" + i + "]");
            if (!seen.add(rule.id())) {
                throw new ConfigException("Duplicate rule id '" + rule.id() + "' in registry");
            }
            rules.add(rule);
        }
        return new RuleRegistry(rules);
    }

    private static Rule rule(Map<String, Object> e, String path) {
        checkKeys(e, KEYS, path);
        for (String required : List.of("id", "tool", "native", "default", "category", "agent")) {
            if (!e.containsKey(required)) {
                throw new ConfigException("Missing key '" + required + "' at '" + path + "'");
            }
        }
        String id = string(e.get("id"), path + ".id");
        Tool tool = Tool.fromKey(string(e.get("tool"), path + ".tool"))
                .orElseThrow(() -> new ConfigException("Unknown tool '" + e.get("tool") + "' at '" + id + "'"));
        return new Rule(
                id,
                tool,
                string(e.get("native"), path + ".native"),
                enumValue(RuleLevel.class, l -> l.name().toLowerCase(Locale.ROOT), e.get("default"), id + ".default"),
                string(e.get("category"), path + ".category"),
                e.containsKey("sonar") ? strings(e.get("sonar"), id + ".sonar") : List.of(),
                string(e.get("agent"), path + ".agent"),
                e.containsKey("properties") ? properties(e.get("properties"), id + ".properties") : Map.of());
    }

    private static Map<String, String> properties(Object node, String path) {
        Map<String, String> out = new LinkedHashMap<>();
        map(node, path).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }
}
