# Spring preset

The Spring preset adds seven ArchUnit rules (`archunit.spring.*`) and a Spring section in `AGENTS.md`. The rules
live in `javacite-archunit` and match Spring annotations by name, so the library has no Spring dependency.

## Detection

`spring: auto` (the default) turns the preset on when Spring Boot is on the project:

| Build | Detection |
|---|---|
| Gradle | `org.springframework.boot:spring-boot` is on the resolved `compileClasspath`, checked at execution time. The result is written to `build/javacite/spring.detected`. |
| Maven | The project declares `spring-boot` or a `spring-boot-starter*` dependency (including ones managed through the Spring Boot parent). |

Force it with `spring: on` or `spring: off` in `javacite.yml`. When the preset is off, the `archunit.spring.*` rules
are off and the Spring section disappears from `AGENTS.md`.

## Adding `ArchitectureTest`

ArchUnit rules run as a normal JUnit 5 test. `init` writes the test for you (to `src/test/java/<base package>/`,
never overwriting an existing file), but you must add the dependency yourself because `init` does not edit build
scripts:

```kotlin
// Gradle
dependencies {
    testImplementation("io.github.wraithyy:javacite-archunit:0.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

```xml
<!-- Maven -->
<dependency>
  <groupId>io.github.wraithyy</groupId>
  <artifactId>javacite-archunit</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

```java
package com.acme.shop;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.github.wraithyy.javacite.archunit.JavaciteRules;
import io.github.wraithyy.javacite.archunit.JavaciteSpringRules;

@AnalyzeClasses(packages = "com.acme.shop", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests JAVACITE = ArchTests.in(JavaciteRules.class);

    @ArchTest
    static final ArchTests SPRING = ArchTests.in(JavaciteSpringRules.class);
}
```

`JavaciteRules` holds the framework-independent rules (`NoFieldInjection`, `NoSystemOut`, `NoJavaUtilLogging`,
`NoGenericExceptions`, `NoPackageCycles`). Leave out the `SPRING` line in non-Spring projects.

The rules read their on/off switches from `javacite.yml`, found through the `javacite.config` system property or by
walking up from `user.dir`. A rule set to `off` there is skipped rather than failed. Rules also pass when no class
matches (a project without controllers is fine).

## The rules

All are `error` by default. Shown: what is rejected, then the fix.

### `archunit.spring.ConstructorInjectionOnly`

No `@Autowired` on fields or methods.

```java
// bad
@Service
class OrderService {
    @Autowired private OrderRepository repository;
}

// good
@Service
class OrderService {
    private final OrderRepository repository;

    OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

### `archunit.spring.LayeredArchitecture`

Controller, Service and Repository layers (by stereotype annotation). Controllers are not accessed by any layer;
services are accessed only by controllers and services; repositories only by services and repositories. A layer you
do not use is simply absent.

```java
// bad: controller skips the service layer
@RestController
class OrderController {
    private final OrderRepository repository;
}

// good
@RestController
class OrderController {
    private final OrderService service;
}
```

### `archunit.spring.NoTransactionalOnControllers`

```java
// bad
@RestController
@Transactional
class OrderController {}

// good: transaction boundary on the service
@Service
class OrderService {
    @Transactional
    void place(Order order) {}
}
```

### `archunit.spring.NoEntitiesInControllerSignatures`

Public controller methods must not return or accept `@Entity` types, including as generic arguments.

```java
// bad
@GetMapping("/orders/{id}")
OrderEntity get(@PathVariable long id) { ... }

// good
@GetMapping("/orders/{id}")
OrderDto get(@PathVariable long id) { ... }
```

### `archunit.spring.NoMockBeanInUnitTests`

A class with `@MockBean` or `@MockitoBean` fields must be a `@SpringBootTest`, `@WebMvcTest` or `@DataJpaTest`.

```java
// bad: pulls Spring semantics into what should be a plain unit test
class OrderServiceTest {
    @MockBean OrderRepository repository;
}

// good
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository repository;
}
```

### `archunit.spring.ServiceNaming` and `archunit.spring.RepositoryNaming`

```java
// bad
@Service class OrderManager {}
@Repository interface OrderStore {}

// good
@Service class OrderService {}
@Repository interface OrderRepository {}
```

## Tips

- `@SpringBootApplication` classes can trip `pmd.InstantiableUtilityClass`-style utility-class rules (Spring needs a
  non-private constructor). If one fires on your application class, opt that one rule out and leave a comment.
- Relax one rule without losing the others: `archunit.spring.ServiceNaming: off`.
