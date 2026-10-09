package io.github.wraithyy.javacite.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * Spring-specific rules. Annotations are matched by name so Spring is not a dependency of this library; include
 * with {@code ArchTests.in(JavaciteSpringRules.class)} in Spring projects only.
 */
public final class JavaciteSpringRules {

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String SERVICE = "org.springframework.stereotype.Service";
    private static final String REPOSITORY = "org.springframework.stereotype.Repository";
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String ENTITY = "jakarta.persistence.Entity";
    private static final String MOCK_BEAN = "org.springframework.boot.test.mock.mockito.MockBean";
    private static final String MOCKITO_BEAN = "org.springframework.test.context.bean.override.mockito.MockitoBean";
    private static final String[] SLICE_TESTS = {
        "org.springframework.boot.test.context.SpringBootTest",
        "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
        "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest"
    };

    @ArchTest
    public static final ArchRule CONSTRUCTOR_INJECTION_ONLY = constructorInjectionOnly(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule LAYERED_ARCHITECTURE = layeredArchitecture(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_TRANSACTIONAL_ON_CONTROLLERS =
            noTransactionalOnControllers(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_ENTITIES_IN_CONTROLLER_SIGNATURES =
            noEntitiesInControllerSignatures(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule NO_MOCK_BEAN_IN_UNIT_TESTS = noMockBeanInUnitTests(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule SERVICE_NAMING = serviceNaming(JavaciteRuleSwitches.current());

    @ArchTest
    public static final ArchRule REPOSITORY_NAMING = repositoryNaming(JavaciteRuleSwitches.current());

    private JavaciteSpringRules() {}

    /** Controller stereotype, directly or through a meta-annotation such as {@code @RestController}. */
    private static DescribedPredicate<JavaClass> controller() {
        return describe(
                "annotated with @Controller or @RestController",
                c -> c.isAnnotatedWith(CONTROLLER)
                        || c.isAnnotatedWith(REST_CONTROLLER)
                        || c.isMetaAnnotatedWith(CONTROLLER));
    }

    private static ArchRule gate(String id, JavaciteRuleSwitches s, ArchRule rule) {
        return GatedRule.of("archunit.spring." + id, s, rule);
    }

    static ArchRule constructorInjectionOnly(JavaciteRuleSwitches s) {
        return gate(
                "ConstructorInjectionOnly",
                s,
                CompositeArchRule.of(noFields().should().beAnnotatedWith(AUTOWIRED))
                        .and(noMethods().should().beAnnotatedWith(AUTOWIRED))
                        .as("Spring beans should use constructor injection only"));
    }

    static ArchRule layeredArchitecture(JavaciteRuleSwitches s) {
        return gate(
                "LayeredArchitecture",
                s,
                Architectures.layeredArchitecture()
                        .consideringOnlyDependenciesInLayers()
                        .withOptionalLayers(true)
                        .layer("Controller")
                        .definedBy(controller())
                        .layer("Service")
                        .definedBy(annotatedWith(SERVICE))
                        .layer("Repository")
                        .definedBy(annotatedWith(REPOSITORY))
                        .whereLayer("Controller")
                        .mayNotBeAccessedByAnyLayer()
                        .whereLayer("Service")
                        .mayOnlyBeAccessedByLayers("Controller", "Service")
                        .whereLayer("Repository")
                        .mayOnlyBeAccessedByLayers("Service", "Repository"));
    }

    static ArchRule noTransactionalOnControllers(JavaciteRuleSwitches s) {
        return gate(
                "NoTransactionalOnControllers",
                s,
                CompositeArchRule.of(
                                com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                                        .that(controller())
                                        .should()
                                        .beAnnotatedWith(TRANSACTIONAL))
                        .and(noMethods()
                                .that()
                                .areDeclaredInClassesThat(controller())
                                .should()
                                .beAnnotatedWith(TRANSACTIONAL))
                        .as("controllers should not be @Transactional"));
    }

    static ArchRule noEntitiesInControllerSignatures(JavaciteRuleSwitches s) {
        ArchCondition<JavaMethod> condition = new ArchCondition<>("not expose @Entity types in signatures") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                var types = new java.util.ArrayList<JavaClass>(method.getReturnType().getAllInvolvedRawTypes());
                method.getParameterTypes().forEach(t -> types.addAll(t.getAllInvolvedRawTypes()));
                for (JavaClass t : types) {
                    if (t.isAnnotatedWith(ENTITY)) {
                        events.add(SimpleConditionEvent.violated(
                                method, method.getDescription() + " exposes entity " + t.getName()));
                    }
                }
            }
        };
        return gate(
                "NoEntitiesInControllerSignatures",
                s,
                methods()
                        .that()
                        .areDeclaredInClassesThat(controller())
                        .and()
                        .haveModifier(JavaModifier.PUBLIC)
                        .should(condition));
    }

    static ArchRule noMockBeanInUnitTests(JavaciteRuleSwitches s) {
        DescribedPredicate<JavaClass> usesMockBean = describe(
                "use @MockBean or @MockitoBean",
                c -> c.getFields().stream()
                        .anyMatch(f -> f.isAnnotatedWith(MOCK_BEAN) || f.isAnnotatedWith(MOCKITO_BEAN)));
        ArchCondition<JavaClass> sliceTest = have(describe(
                "an integration or slice test annotation",
                c -> java.util.Arrays.stream(SLICE_TESTS).anyMatch(c::isAnnotatedWith)));
        return gate(
                "NoMockBeanInUnitTests",
                s,
                classes()
                        .that(usesMockBean)
                        .should(sliceTest)
                        .as("classes using @MockBean should be @SpringBootTest, @WebMvcTest or @DataJpaTest"));
    }

    static ArchRule serviceNaming(JavaciteRuleSwitches s) {
        return gate(
                "ServiceNaming",
                s,
                classes().that().areAnnotatedWith(SERVICE).should().haveSimpleNameEndingWith("Service"));
    }

    static ArchRule repositoryNaming(JavaciteRuleSwitches s) {
        return gate(
                "RepositoryNaming",
                s,
                classes().that().areAnnotatedWith(REPOSITORY).should().haveSimpleNameEndingWith("Repository"));
    }
}
