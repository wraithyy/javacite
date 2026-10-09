<!-- javacite:start -->
# javacite code standards

## Quick reference

- Check: `./gradlew javaciteCheck`
- Fix (format): `./gradlew javaciteFix`
- Doctor: `./gradlew javaciteDoctor`

## Core principles

- Write code that passes the javacite checks on the first run.
- Prefer simple, explicit code over clever abstractions.
- Handle every error path; never swallow exceptions.
- Keep methods small and classes focused on one responsibility.
- Do not suppress a rule to make a check pass; fix the cause or ask first.

## Null safety

- Annotate nullable values with @Nullable and never dereference possibly null values.

## Correctness

- Avoid local variables or parameters that shadow fields.
- Never catch Exception, RuntimeException or Throwable; catch the specific types.
- Never throw Error, RuntimeException or Throwable; throw a specific exception type.
- Never reassign method parameters; copy into a local variable.
- Never compare strings with == or !=; use equals.
- Override hashCode whenever equals is overridden.
- Call equals on the string literal or known non-null operand.
- Override equals(Object), never an overload taking a narrower type.
- Never leave a stray semicolon as an empty statement.
- End each switch case with break, return or throw, or mark intentional fall-through.
- Always provide a default branch in switch statements.
- Never modify a for-loop control variable inside the loop body.
- Declare a package that matches the directory structure.
- Never use double brace initialization; use factory methods such as List.of.
- Never leave a code block empty; add logic or a comment explaining why.
- Never swallow exceptions silently; handle, log or rethrow them.
- Name the file after its outer type.
- Avoid assignments inside operands.
- Never assign static fields from constructors.
- Avoid break or continue as the last statement of a loop.
- Extract repeated string literals into constants.
- Never use octal literals.
- Always close resources, preferably with try-with-resources.
- Compare objects with equals, not ==.
- Use Double.isNaN instead of comparing with NaN.
- Never call System.gc or Runtime.gc.
- Never leave a catch block empty.
- Never call equals(null).
- Never rely on implicit switch fall-through.
- Check for null before dereferencing, not after.
- Implement clone via super.clone.
- Declare loggers as private static final with the class name.
- Return empty collections or arrays, never null.
- Never return from a finally block.
- Pass an explicit Locale to SimpleDateFormat, or prefer java.time.
- Remove if statements with constant conditions.
- Use the context class loader instead of getClass().getClassLoader() in Jakarta EE code.
- Always annotate overriding methods with @Override.
- Remove unused variables, fields and parameters.
- Remove unused private methods.
- Never ignore the Future returned by async calls; check or chain it.
- Avoid String.split; use Splitter-style helpers or Pattern.split with explicit limit.
- Keep enum fields final and deeply immutable.
- Avoid getClass in equals; prefer instanceof with final classes or records.
- Handle every enum constant in switch statements.
- Always pass an explicit Charset, preferably StandardCharsets.UTF_8.
- Always pass an explicit ZoneId or Clock to java.time now calls.
- Annotate trivial deprecated delegating methods with @InlineMe.
- Add parentheses when mixing operators with confusing precedence.
- Compare objects with equals, not ==, unless they are enums or identity-based.
- End switch cases with break, return or throw.
- Return consistently mutable or immutable collections from a method.
- Avoid type parameters that appear only in the return type.
- Pass the caught exception as cause or use it when rethrowing.
- Prefer methods over constant lambdas stored in fields.
- Declare inner classes static when they do not use the outer instance.
- Never leave catch blocks empty.
- Avoid obsolete JDK classes such as Vector, Hashtable and StringBuffer.
- Never name types after java.lang classes.
- Never pass a long to a double parameter where precision can be lost.
- Never expose public static final arrays.
- Never call toString on types that inherit Object.toString.
- Pass Locale.ROOT or an explicit Locale to toLowerCase and toUpperCase.
- Never print stack traces in catch blocks; log them.
- Never import nested types with ambiguous simple names such as Builder.
- Never name fields and variables differing only by capitalization.
- Declare format strings inline or as static final constants.
- Use && and || instead of & and | on booleans.
- Remove redundant parentheses.
- Replace anonymous classes that can be lambdas.
- Remove unused labels.
- Remove unused nested classes.
- Give every Javadoc comment a summary sentence.
- Declare one top-level class per file.
- Use getDeclaredConstructor().newInstance instead of Class.newInstance.
- Never catch an exception in a test only to call fail; declare throws.
- Never use double brace initialization.
- Write floating-point literals that are exactly representable or explain the rounding.
- Check the type with instanceof before casting in equals.
- Fix SpotBugs correctness bugs: they are almost always real defects.
- Avoid SpotBugs bad-practice patterns such as ignoring return values or violating equals contracts.

## Design

- Avoid Object.clone; use copy constructors or factory methods.
- Never implement finalize; use try-with-resources or Cleaner.
- Declare classes with only private constructors final.
- Give utility classes a private constructor.
- Keep exception fields final so exceptions stay immutable.
- Declare at most two checked exceptions per method; wrap or group them.
- Keep fields private and expose them through methods.
- Never throw NullPointerException; use Objects.requireNonNull or IllegalArgumentException.
- Remove catch blocks that only rethrow the same exception.
- Merge nested if statements with a combined condition.
- Keep cognitive complexity of methods low; extract helper methods.
- Keep cyclomatic complexity of methods low; split large methods.
- Never use exceptions for ordinary control flow.
- Make constant final fields static.
- Declare fields final when they are assigned only at construction.
- Use the inverse operator instead of negating a comparison.
- Simplify ternaries that return boolean literals.
- Remove null checks that instanceof already covers.
- Make fields that are used in one method local variables.
- Never declare throws Exception; declare the specific exception types.
- Remove overriding methods that only call super.
- Make classes with only static members utility classes.
- Keep NPath complexity of methods low.
- Avoid deeply nested if statements; use guard clauses.

## Style

- Declare local variables final when they are never reassigned.
- Extract magic numbers into named constants.
- Declare one variable per statement.
- Avoid redundant boolean literals in conditions.
- Return a boolean condition directly instead of branching to return literals.
- Place the default branch last in a switch.
- Avoid assignments inside sub-expressions.
- Put one statement on each line.
- Remove redundant parentheses.
- Always use braces around if, else, for, while and do bodies.
- Avoid standalone nested blocks.
- Declare nested types after fields, constructors and methods.
- Declare exactly one top-level type per file.
- Order modifiers as in the Java Language Specification.
- Remove modifiers that are implied, such as public on interface methods.
- Declare method and constructor parameters final.
- Declare arrays Java-style as String[] args, never String args[].
- Write long literals with an uppercase L suffix.
- Remove unused, duplicate and unnecessary imports.
- Import types instead of using fully qualified names.
- Remove modifiers that are implied by context.
- Remove return statements at the end of void methods.
- Remove superfluous semicolons.
- Remove constructors that only duplicate the default constructor.
- Remove redundant casts.
- Remove redundant boxing and unboxing.
- Remove parentheses that do not change evaluation.
- Remove redundant qualification of this.
- Use a while loop when a for loop has no init or update.
- Merge catch branches with identical bodies into a multi-catch.
- Always use braces for control statements.
- Never write extends Object.
- Never leave control statements with an empty body.
- Use the diamond operator for generic instantiation.
- Prefer method references over trivial lambdas.
- Use short array initializer syntax in declarations.
- Never use dollar signs in identifiers.
- Declare fields before constructors and methods.
- Follow class naming conventions.
- Follow method naming conventions.
- Follow local variable naming conventions.
- Follow parameter naming conventions.
- Use lowercase package names.
- Review SpotBugs dodgy-code findings such as redundant null checks and dead stores. (warning)

## Architecture

- Use constructor injection, never field injection with @Autowired, @Inject or @Resource.
- Use a logger instead of System.out and System.err.
- Use SLF4J, never java.util.logging.
- Never throw or declare Exception, RuntimeException or Throwable; use specific types.
- Keep top-level packages free of cyclic dependencies.

## Security

- Fix SpotBugs security findings such as SQL string building, weak crypto and path traversal.

## Performance

- Use String.valueOf instead of concatenating an empty string.
- Append single characters as char, not String.
- Prefer Files.newInputStream and Files.newOutputStream over FileInputStream and FileOutputStream.
- Use BigInteger and BigDecimal constants such as ZERO and ONE.
- Chain consecutive StringBuilder appends.
- Merge consecutive literal appends into one literal.
- Use isEmpty or isBlank instead of trimming to test emptiness.
- Pass a zero-length array to Collection.toArray.
- Never initialise fields to their default values.
- Never call new String(String).
- Never call toString on a String.
- Use Arrays.asList or List.of instead of manual array copy loops.
- Remove String.valueOf inside string concatenation.
- Avoid SpotBugs performance anti-patterns such as needless boxing and inefficient collection use.

## Best practices

- Never call printStackTrace; log the exception with the logger.
- Never hard-code IP addresses; read them from configuration.
- Check the boolean result of ResultSet navigation calls.
- Never declare constants in interfaces; use a final class or enum.
- Prefer enhanced for loops when the index is unused.
- Declare variables with interface types such as List or Map, not implementations.
- Pass the caught exception as cause when rethrowing a new exception.
- Remove assignments whose value is never read.
- Remove unused method parameters.
- Remove unused local variables.
- Remove unused private fields.
- Remove unused private methods.
- Use isEmpty instead of comparing size to zero.
- Put literals first when comparing with equals.
- Return a copy of internal arrays, never the array itself.
- Copy arrays passed to constructors and setters before storing them.
- Use StandardCharsets constants instead of charset name strings.
- Prefer try-with-resources over manual close in finally blocks.
- Avoid while loops with a literal boolean condition other than deliberate loops.
- Prefer Map implementations over the legacy Hashtable.

## Complexity

- Avoid nesting for loops more than one level deep.
- Avoid nesting if statements more than two levels deep; use guard clauses.
- Keep methods short; split methods longer than 100 lines.
- Limit methods to seven parameters; introduce a parameter object.

## Concurrency

- Synchronize on a private lock object instead of whole methods.
- Never use ThreadGroup.
- Call Thread.start, never Thread.run.
- Never use double-checked locking without volatile; prefer holder idiom.
- Make lazily initialised singletons thread safe.
- Never share DateFormat or NumberFormat statically without synchronization.
- Prefer ConcurrentHashMap for maps shared between threads.
- Prefer notifyAll over notify.
- Fix SpotBugs multithreading bugs: unsynchronized access, wait without loop, inconsistent locking.

## Documentation

- Document every public method with Javadoc.
- Document every public type with Javadoc.
- Keep Javadoc parameters, returns and throws in sync with the signature.
- Place Javadoc directly before the declaration it describes.
- Give every Javadoc block tag a description.
- Write a meaningful first sentence in Javadoc, never a placeholder.

## Imports

- Never use star imports; import each class explicitly.
- Never import internal sun.* packages.
- Remove duplicate and same-package imports.
- Remove unused imports.

## Naming

- Name constants in UPPER_SNAKE_CASE.
- Name local variables in lowerCamelCase.
- Name instance fields in lowerCamelCase.
- Name methods in lowerCamelCase.
- Name parameters in lowerCamelCase.
- Name static fields in lowerCamelCase.
- Name types in UpperCamelCase.

## Spring

- Inject Spring beans through constructors only.
- Respect controller to service to repository layering; never skip or reverse a layer.
- Never put @Transactional on controllers; place it on services.
- Never expose JPA entities in controller signatures; use DTOs.
- Use plain Mockito mocks in unit tests; reserve @MockBean for slice tests.
- Name @Service classes with a Service suffix.
- Name repository interfaces with a Repository suffix.

## What javacite cannot check

Review these yourself; no tool verifies them:

- Business logic correctness
- Whether names express intent
- Architecture beyond layering
- Edge cases and failure scenarios
- User experience
- Documentation accuracy

Before finishing, run `./gradlew javaciteFix` and then `./gradlew javaciteCheck`.
<!-- javacite:end -->
