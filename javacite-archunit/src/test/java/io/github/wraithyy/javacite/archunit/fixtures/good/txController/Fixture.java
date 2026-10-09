package io.github.wraithyy.javacite.archunit.fixtures.good.txController;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

public class Fixture {
    @Service
    public static class WriteService {
        @Transactional
        public void write() {}
    }

    @RestController
    public static class WriteController {
        public void write() {}
    }
}
