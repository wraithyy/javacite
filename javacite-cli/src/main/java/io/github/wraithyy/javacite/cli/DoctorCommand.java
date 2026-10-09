package io.github.wraithyy.javacite.cli;

import io.github.wraithyy.javacite.core.config.ConfigException;
import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.generate.GitHooksGenerator;
import io.github.wraithyy.javacite.core.init.InitWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Reports setup problems; only an unparsable javacite.yml fails the exit code. */
@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Check JDK, build tool, config, agent files and hooks.")
final class DoctorCommand implements Callable<Integer> {

    private static final List<String> AGENT_FILES = List.of(
            "AGENTS.md",
            "CLAUDE.md",
            ".cursor/rules/javacite.mdc",
            ".github/copilot-instructions.md",
            ".windsurf/rules/javacite.md",
            ".claude/settings.json",
            GitHooksGenerator.PRE_COMMIT_PATH);

    @Spec
    CommandSpec spec;

    @Option(names = "--dir", description = "Project root (default: current directory).")
    Path dir = Path.of("").toAbsolutePath();

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        Path root = dir.toAbsolutePath().normalize();
        int exit = 0;

        int jdk = Runtime.version().feature();
        out.println((jdk >= 21 ? "ok   " : "WARN ") + "JDK " + jdk + (jdk >= 21 ? "" : " (21+ required to run the build)"));

        try {
            out.println("ok   build tool: " + BuildFiles.detect(root, null).name().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            out.println("WARN build tool: " + e.getMessage());
        }

        Path config = root.resolve("javacite.yml");
        if (Files.notExists(config)) {
            out.println("WARN javacite.yml missing (run `javacite init`)");
        } else {
            try {
                ConfigLoader.load(BuildFiles.read(config));
                out.println("ok   javacite.yml parses");
            } catch (ConfigException e) {
                out.println("FAIL javacite.yml: " + e.getMessage());
                exit = 1;
            }
        }

        for (String f : AGENT_FILES) {
            out.println((Files.exists(root.resolve(f), java.nio.file.LinkOption.NOFOLLOW_LINKS) ? "ok   " : "WARN missing ") + f);
        }
        String hooks = InitWriter.gitHooksPath(root);
        out.println(hooks.isEmpty() ? "WARN core.hooksPath not set" : "ok   core.hooksPath = " + hooks);
        out.flush();
        return exit;
    }
}
