package io.github.wraithyy.javacite.core.generate;

import java.util.List;

/** The {@code .githooks/pre-commit} script and the command that activates it. */
public final class GitHooksGenerator {

    public static final String PRE_COMMIT_PATH = ".githooks/pre-commit";

    private GitHooksGenerator() {}

    public static String preCommit(BuildTool tool) {
        return preCommit(tool, true);
    }

    /** With {@code spotlessEnabled} false the hook only runs the check; there is nothing to format with. */
    public static String preCommit(BuildTool tool, boolean spotlessEnabled) {
        if (!spotlessEnabled) {
            return ClaudeHooksGenerator.Templates.load("pre-commit-check-only")
                    .replace("{{CHECK_CMD}}", tool.quietCheckCommand());
        }
        return ClaudeHooksGenerator.Templates.load("pre-commit")
                .replace("{{FORMAT_FILES_CMD}}", tool.formatFilesCommand())
                .replace("{{CHECK_CMD}}", tool.quietCheckCommand());
    }

    /** Command the caller runs once to activate the hooks directory. */
    public static List<String> activationCommand() {
        return List.of("git", "config", "core.hooksPath", ".githooks");
    }
}
