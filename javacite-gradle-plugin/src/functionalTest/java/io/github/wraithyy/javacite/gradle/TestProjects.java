package io.github.wraithyy.javacite.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.testkit.runner.GradleRunner;

/** Shared TestKit plumbing: file writing and a runner that always uses the configuration cache. */
final class TestProjects {

    private TestProjects() {}

    static void write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Runner with the plugin classpath; Sonar variables are stripped so the host's setup cannot leak in. */
    static GradleRunner runner(Path dir, String warningMode, Map<String, String> env, String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--configuration-cache";
        all[1] = "--warning-mode=" + warningMode;
        System.arraycopy(args, 0, all, 2, args.length);
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.remove("SONAR_HOST_URL");
        environment.remove("SONAR_TOKEN");
        environment.putAll(env);
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withEnvironment(environment)
                .withArguments(all);
    }
}
