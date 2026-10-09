package io.github.wraithyy.javacite.core.generate;

/** Build tool of the target project; carries the shell commands generated files call. */
public enum BuildTool {
    GRADLE(
            "./gradlew javaciteCheck",
            "./gradlew javaciteFix",
            "./gradlew javaciteDoctor",
            "./gradlew -q spotlessApply -PspotlessIdeHook=\"$FILE\"",
            "./gradlew -q spotlessApply -PspotlessFiles=\"$REGEX\"",
            "./gradlew -q javaciteCheck"),
    MAVEN(
            "mvn verify",
            "mvn spotless:apply",
            "mvn io.github.wraithyy:javacite-maven-plugin:doctor",
            "mvn -q spotless:apply -DspotlessFiles=\"$FILE\"",
            "mvn -q spotless:apply -DspotlessFiles=\"$REGEX\"",
            "mvn -q -DskipTests verify");

    private final String check;
    private final String fix;
    private final String doctor;
    private final String formatFile;
    private final String formatFiles;
    private final String quietCheck;

    BuildTool(String check, String fix, String doctor, String formatFile, String formatFiles, String quietCheck) {
        this.check = check;
        this.fix = fix;
        this.doctor = doctor;
        this.formatFile = formatFile;
        this.formatFiles = formatFiles;
        this.quietCheck = quietCheck;
    }

    public String checkCommand() {
        return check;
    }

    public String fixCommand() {
        return fix;
    }

    public String doctorCommand() {
        return doctor;
    }

    /** Shell snippet formatting the file in {@code $FILE}; the variable is expanded by the generated script. */
    public String formatFileCommand() {
        return formatFile;
    }

    /** Shell snippet formatting every file matched by the regex in {@code $REGEX} in one invocation. */
    public String formatFilesCommand() {
        return formatFiles;
    }

    /** Non-interactive check used by hooks (quiet, no tests on Maven). */
    public String quietCheckCommand() {
        return quietCheck;
    }
}
