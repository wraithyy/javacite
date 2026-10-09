package io.github.wraithyy.javacite.archunit.fixtures.bad.mockBean;

import org.springframework.boot.test.mock.mockito.MockBean;

@SuppressWarnings({"deprecation", "removal"})
public class Fixture {
    public static class Dep {}

    public static class PlainTest {
        @MockBean private Dep dep;
    }
}
