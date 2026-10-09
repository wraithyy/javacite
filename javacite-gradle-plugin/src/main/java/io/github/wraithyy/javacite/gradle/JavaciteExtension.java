package io.github.wraithyy.javacite.gradle;

import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/** The {@code javacite} extension: read-only access to the parsed {@code <rootProject>/javacite.yml}. */
public abstract class JavaciteExtension {

    /** Gradle-managed storage behind {@link #getConfig()}; set once by the plugin. */
    abstract Property<JavaciteConfig> getConfigStorage();

    /** Parsed config; reads the file through a value source so the configuration cache tracks it. */
    public Provider<JavaciteConfig> getConfig() {
        return getConfigStorage();
    }
}
