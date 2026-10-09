package io.github.wraithyy.javacite.archunit.fixtures.good.mockBean;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SuppressWarnings({"deprecation", "removal"})
public class Fixture {
    public static class Dep {}

    @SpringBootTest
    public static class IntegrationTest {
        @MockBean private Dep dep;
    }
}
