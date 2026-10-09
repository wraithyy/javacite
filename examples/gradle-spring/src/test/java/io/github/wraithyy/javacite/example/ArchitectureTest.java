package io.github.wraithyy.javacite.example;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.github.wraithyy.javacite.archunit.JavaciteRules;
import io.github.wraithyy.javacite.archunit.JavaciteSpringRules;

@AnalyzeClasses(packages = "io.github.wraithyy.javacite.example", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    @ArchTest
    static final ArchTests JAVACITE = ArchTests.in(JavaciteRules.class);

    @ArchTest
    static final ArchTests SPRING = ArchTests.in(JavaciteSpringRules.class);

    private ArchitectureTest() {}
}
