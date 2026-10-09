package io.github.wraithyy.javacite.core.spring;

/** The single definition of "this dependency means Spring Boot"; every build-tool integration must use it. */
public final class SpringArtifacts {

    private SpringArtifacts() {}

    /** True only for the {@code org.springframework.boot:spring-boot} module (not starters or other boot-* modules). */
    public static boolean isSpringBoot(String group, String name) {
        return "org.springframework.boot".equals(group) && "spring-boot".equals(name);
    }
}
