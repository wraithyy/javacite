package io.github.wraithyy.javacite.core.config;

import java.util.Map;

/** A tool is on, off, or on with an options object. */
public sealed interface ToolSetting {

    default boolean enabled() {
        return !(this instanceof Off);
    }

    record On() implements ToolSetting {}

    record Off() implements ToolSetting {}

    /** Raw validated options; typed views live on {@link JavaciteConfig}. */
    record Options(Map<String, Object> values) implements ToolSetting {
        public Options {
            values = Map.copyOf(values);
        }
    }
}
