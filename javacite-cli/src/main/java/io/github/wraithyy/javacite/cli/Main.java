package io.github.wraithyy.javacite.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point of {@code javacite}: bootstrap and diagnose a project from the command line. */
@Command(
        name = "javacite",
        mixinStandardHelpOptions = true,
        versionProvider = Main.Version.class,
        subcommands = {InitCommand.class, DoctorCommand.class})
public final class Main implements Runnable {

    static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v == null ? "0.1.0-SNAPSHOT" : v;
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"javacite " + version()};
        }
    }
}
