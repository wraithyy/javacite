# Agents: AGENTS.md, hooks and pre-commit

`init` turns the resolved rule set into files that coding agents and git read. The generated text reflects your real
configuration: opt a rule or tool out and it disappears from `AGENTS.md`.

```sh
./gradlew javaciteInit                                  # Gradle
mvn io.github.wraithyy:javacite-maven-plugin:init       # Maven
jbang javacite@wraithyy/javacite init                   # either, from the project root
```

`init` is idempotent: a second run reports `unchanged` for everything. Re-run it whenever you change `javacite.yml`
or upgrade javacite.

## What `init` generates

Controlled by `agents.targets` in [javacite.yml](configuration.md#agentstargets). All targets are on by default.

| Target | File | Notes |
|---|---|---|
| `agents-md` | `AGENTS.md` | Marker block, see below. |
| `claude-md` | `CLAUDE.md` | Symlink to `AGENTS.md`. If a regular `CLAUDE.md` already exists, the block is merged into it instead. Without symlink support (Windows) a copy of the block is written. |
| `cursor` | `.cursor/rules/javacite.mdc` | Same block with `alwaysApply: true` front matter. |
| `copilot` | `.github/copilot-instructions.md` | Marker block. |
| `windsurf` | `.windsurf/rules/javacite.md` | Marker block. |
| `claude-hooks` | `.javacite/hook-format.sh`, `.javacite/hook-check.sh`, `.claude/settings.json` | Settings are merged, see below. |
| `git-hooks` | `.githooks/pre-commit` | Plus `git config core.hooksPath .githooks` when `.git` exists. |

Besides these, `init` writes `javacite.yml` when absent and, if `tools.archunit` is on and `src/main/java` has
sources, `ArchitectureTest.java` (an existing file is never touched). With the jbang CLI, `--targets a,b,c` replaces
`agents.targets` for that run and `--no-git-hooks` skips the git hook.

## AGENTS.md block semantics

javacite owns only the text between two markers:

```markdown
# My project

Notes written by hand, kept as they are.

<!-- javacite:start -->
# javacite code standards
...generated...
<!-- javacite:end -->

More hand-written notes, also kept.
```

- On every run the block between the markers is replaced in place. Everything outside is preserved byte for byte.
- If the file has no markers, the block is appended after a blank line.
- Do not edit inside the markers; the next `init` overwrites it. Change `javacite.yml` instead.

The block contains: quick-reference commands for your build tool (check, fix, doctor), core principles, rules grouped
by category (null safety, correctness, design, style, architecture, security, performance, naming, imports, ...), a
Spring section when the preset is active, and a list of what javacite cannot check. Rules at `warn` carry a
`(warning)` suffix. See `examples/gradle-spring/AGENTS.md` for a real one.

## Claude Code hooks

`.claude/settings.json` gets two entries. Existing hooks and unrelated keys are kept, an entry whose script is
already registered is updated in place, so re-running never duplicates.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.javacite/hook-format.sh",
            "if": "Edit(*.java)"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          { "type": "command", "command": "\"$CLAUDE_PROJECT_DIR\"/.javacite/hook-check.sh" }
        ]
      }
    ]
  }
}
```

**PostToolUse, `hook-format.sh`.** Runs after Claude edits or writes a file. The `if: "Edit(*.java)"` rule keeps
non-Java edits from spawning a process; the script also re-checks the extension. It reads the hook payload on
stdin, takes `tool_input.file_path` (with `jq`, falling back to `sed`) and formats only that file:

- Gradle: `./gradlew -q spotlessApply -PspotlessIdeHook="$FILE"`
- Maven: `mvn -q spotless:apply -DspotlessFiles="$FILE"`

Formatting is best effort: a failure never blocks the edit.

**Stop, `hook-check.sh`.** Runs when Claude finishes a turn. It runs the quiet check (`./gradlew -q javaciteCheck`,
or `mvn -q -DskipTests verify` on Maven: no tests, no network). On failure it prints the last 40 lines to stderr and
exits 2, which makes Claude Code hand the output back to the agent to fix. The script reads `stop_hook_active` from
the payload and exits 0 when it is true, so a check that keeps failing cannot trap the agent in a loop: the agent
gets one chance per stop to fix, then control returns to you.

Both scripts honour overrides from the environment:

| Variable | Replaces |
|---|---|
| `JAVACITE_FORMAT_CMD` | The format command; called with the file path as its argument. |
| `JAVACITE_CHECK_CMD` | The check command. |

## Git pre-commit

`.githooks/pre-commit` formats the staged `.java` files, re-stages them, then runs the quiet check. Formatting is
batched: all staged files go through one build-tool invocation (a single Spotless call with a path regex), not one per
file, because a Gradle or Maven start costs seconds. Paths with spaces are handled. Set `JAVACITE_FORMAT_CMD` to
format file by file with your own command instead, and `JAVACITE_CHECK_CMD` to change the check.

`init` activates it with `git config core.hooksPath .githooks`. That setting is per clone, so teammates run
`git config core.hooksPath .githooks` once (or `init` again). This conflicts with tools that also own
`core.hooksPath`; if you already use Lefthook or Husky, drop `git-hooks` from `agents.targets` and call
`./gradlew javaciteCheck` from your existing hook.

## Trust surface

After `init`, `.githooks/`, `.javacite/`, `.claude/settings.json` and the Gradle wrapper (`gradle/wrapper/`, `gradlew`)
execute on every commit and every agent stop, so a change to any of them is a change to code that runs on every
developer machine. Protect these paths with CODEOWNERS (and branch protection requiring that review). `init` prints a
note when it activates `core.hooksPath`, so the switch is never silent.

## Customising

- Add your own project rules to `AGENTS.md` outside the markers.
- Remove a generated file you do not want by dropping its target from `agents.targets`; `init` stops writing it
  (it does not delete files that already exist).
- Edit the hook scripts freely if you remove `claude-hooks` from `agents.targets` first; otherwise `init` rewrites
  them to the generated content.
- `./gradlew javaciteDoctor` (or `jbang javacite@wraithyy/javacite doctor`) lists which generated files are missing.
