package io.github.wraithyy.javacite.archunit.fixtures.bad.julLogging;

import java.util.logging.Logger;

public class Fixture {
    private static final Logger LOG = Logger.getLogger("x");

    public Logger log() {
        return LOG;
    }
}
