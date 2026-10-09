package io.github.wraithyy.javacite.core.config;

/** Whether Sonar wiring is applied; AUTO means when SONAR_HOST_URL or SONAR_TOKEN is set. */
public enum SonarMode {
    AUTO,
    ON,
    OFF
}
