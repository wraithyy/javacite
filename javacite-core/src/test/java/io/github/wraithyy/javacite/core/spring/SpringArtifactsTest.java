package io.github.wraithyy.javacite.core.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringArtifactsTest {

    @Test
    void matchesExactSpringBootCoordinates() {
        assertThat(SpringArtifacts.isSpringBoot("org.springframework.boot", "spring-boot"))
                .isTrue();
    }

    @Test
    void rejectsOtherBootModulesGroupsAndNames() {
        assertThat(SpringArtifacts.isSpringBoot("org.springframework.boot", "spring-boot-starter"))
                .isFalse();
        assertThat(SpringArtifacts.isSpringBoot("org.springframework.boot", "spring-boot-autoconfigure"))
                .isFalse();
        assertThat(SpringArtifacts.isSpringBoot("org.springframework", "spring-boot"))
                .isFalse();
        assertThat(SpringArtifacts.isSpringBoot("", "spring-boot")).isFalse();
    }
}
