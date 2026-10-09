package io.github.wraithyy.javacite.cli;

import io.github.wraithyy.javacite.core.generate.BuildTool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Build tool detection and the small text edits javacite makes to build files. */
final class BuildFiles {

    static final String PLUGIN_ID = "io.github.wraithyy.javacite";
    static final String EXTENSION_ARTIFACT = "javacite-maven-plugin";

    private BuildFiles() {}

    /** Detects by marker files; {@code flag} (gradle|maven, may be null) decides when both are present. */
    static BuildTool detect(Path dir, String flag) {
        boolean gradle = Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("settings.gradle.kts"))
                || gradleBuildFile(dir).isPresent();
        boolean maven = Files.exists(dir.resolve("pom.xml"));
        if (flag != null) {
            return switch (flag.toLowerCase(java.util.Locale.ROOT)) {
                case "gradle" -> BuildTool.GRADLE;
                case "maven" -> BuildTool.MAVEN;
                default -> throw new IllegalArgumentException("--build-tool must be gradle or maven, got: " + flag);
            };
        }
        if (gradle && maven) {
            throw new IllegalArgumentException("Both Gradle and Maven files found in " + dir + "; pass --build-tool gradle|maven");
        }
        if (gradle) {
            return BuildTool.GRADLE;
        }
        if (maven) {
            return BuildTool.MAVEN;
        }
        throw new IllegalArgumentException(
                "No build.gradle(.kts), settings.gradle(.kts) or pom.xml in " + dir + "; run from the project root or pass --dir");
    }

    static Optional<Path> gradleBuildFile(Path dir) {
        return Optional.of(dir.resolve("build.gradle.kts"))
                .filter(Files::exists)
                .or(() -> Optional.of(dir.resolve("build.gradle")).filter(Files::exists));
    }

    static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String gradleLine(boolean kotlin, String version) {
        return kotlin
                ? "id(\"" + PLUGIN_ID + "\") version \"" + version + "\""
                : "id '" + PLUGIN_ID + "' version '" + version + "'";
    }

    /**
     * Inserts the plugin id right after a {@code plugins \{} line. Returns empty when the file already applies the
     * plugin or has no plugins block that ends its line (one-liner blocks are not edited).
     */
    static Optional<String> insertPlugin(String buildText, boolean kotlin, String version) {
        if (buildText.contains(PLUGIN_ID)) {
            return Optional.empty();
        }
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?m)^([ \\t]*)plugins[ \\t]*\\{[ \\t]*\\r?\\n").matcher(buildText);
        if (!m.find()) {
            return Optional.empty();
        }
        String line = m.group(1) + "    " + gradleLine(kotlin, version) + "\n";
        return Optional.of(buildText.substring(0, m.end()) + line + buildText.substring(m.end()));
    }

    static String extensionXml(String version) {
        return "    <extension>\n"
                + "        <groupId>io.github.wraithyy</groupId>\n"
                + "        <artifactId>" + EXTENSION_ARTIFACT + "</artifactId>\n"
                + "        <version>" + version + "</version>\n"
                + "    </extension>\n";
    }

    /** Adds the extension to {@code .mvn/extensions.xml} content; returns the input unchanged when present. */
    static String mergeExtensions(String existing, String version) {
        if (existing.isBlank()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<extensions>\n" + extensionXml(version) + "</extensions>\n";
        }
        if (existing.contains(EXTENSION_ARTIFACT)) {
            return existing;
        }
        int end = existing.lastIndexOf("</extensions>");
        if (end < 0) {
            throw new IllegalArgumentException(".mvn/extensions.xml has no </extensions>; add the extension manually");
        }
        return existing.substring(0, end) + extensionXml(version) + existing.substring(end);
    }
}
