package io.github.wraithyy.javacite.core.config;

import java.util.List;

/** How NullAway decides which code is checked. */
public record NullawayMode(Kind kind, List<String> packages) {

    /** YAML spelling is {@link #key()}. */
    public enum Kind {
        ONLY_NULL_MARKED("onlyNullMarked"),
        ANNOTATED_PACKAGES("annotatedPackages");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public NullawayMode {
        packages = List.copyOf(packages);
    }

    public static NullawayMode defaults() {
        return new NullawayMode(Kind.ONLY_NULL_MARKED, List.of());
    }
}
