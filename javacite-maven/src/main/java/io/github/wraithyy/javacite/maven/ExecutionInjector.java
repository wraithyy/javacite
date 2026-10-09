package io.github.wraithyy.javacite.maven;

import io.github.wraithyy.javacite.core.config.DependencyCheckOptions;
import io.github.wraithyy.javacite.core.config.DependencyCheckOptions.InCheck;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.generate.ErrorProneArgsGenerator;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds one {@code javacite} execution per enabled tool to a project model. A plugin the user already declared keeps
 * its own configuration and gets an extra execution; the compiler is the exception because Error Prone has to ride
 * on the default compile executions, so its arguments and processor paths are merged into the user's configuration.
 */
public final class ExecutionInjector {

    public static final String EXECUTION_ID = "javacite";

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionInjector.class);

    private static final String MAVEN_PLUGINS = "org.apache.maven.plugins";
    private static final List<String> JAVAC_EXPORTS =
            List.of("api", "file", "main", "model", "parser", "processing", "tree", "util");
    private static final List<String> JAVAC_OPENS = List.of("code", "comp");

    private final JavaciteConfig config;
    private final Map<String, String> env;
    private final String selfVersion;

    /** @param selfVersion version of this plugin, used to bind generate-configs */
    public ExecutionInjector(JavaciteConfig config, Map<String, String> env, String selfVersion) {
        this.config = config;
        this.env = env;
        this.selfVersion = Objects.requireNonNull(selfVersion, "selfVersion");
    }

    public void inject(MavenProject project, boolean spring) {
        ResolvedRules rules = ResolvedRules.of(config, spring);
        enforcer(project);
        if (on(Tool.PMD)) {
            bind(project, "io.github.wraithyy", "javacite-maven-plugin", selfVersion, List.of(), null, "validate", "generate-configs");
        }
        compiler(project, rules);
        spotless(project);
        pmd(project);
        jacoco(project);
        dependencyCheck(project);
    }

    private boolean on(Tool t) {
        return config.tool(t).enabled();
    }

    private void enforcer(MavenProject p) {
        if (!on(Tool.ENFORCER)) {
            return;
        }
        Xpp3Dom excludes = node("excludes");
        config.bannedDeps().forEach(d -> excludes.addChild(leaf("exclude", d)));
        Xpp3Dom rules = node(
                "rules",
                node("requireJavaVersion", leaf("version", "[21,)")),
                node("bannedDependencies", excludes));
        bind(p, MAVEN_PLUGINS, "maven-enforcer-plugin", "3.6.3", List.of(), node("configuration", rules, leaf("fail", "true")), "validate", "enforce");
    }

    private void compiler(MavenProject p, ResolvedRules rules) {
        boolean nullaway = on(Tool.NULLAWAY);
        boolean errorProne = on(Tool.ERRORPRONE) || nullaway;
        Plugin plugin = find(p.getBuild(), MAVEN_PLUGINS, "maven-compiler-plugin");
        Xpp3Dom userCfg = plugin == null ? null : (Xpp3Dom) plugin.getConfiguration();

        Xpp3Dom ours = node("configuration");
        // The java: level from javacite.yml is the default; an explicit property or plugin-level release wins.
        boolean userRelease = p.getProperties().getProperty("maven.compiler.release") != null
                || (userCfg != null && userCfg.getChild("release") != null);
        if (!userRelease) {
            ours.addChild(leaf("release", String.valueOf(config.java())));
        }
        if (errorProne) {
            ours.addChild(leaf("fork", "true"));
            ours.addChild(compilerArgs(rules));
            Xpp3Dom paths = node("annotationProcessorPaths", path("com.google.errorprone", "error_prone_core", "2.50.0"));
            if (nullaway) {
                paths.addChild(path("com.uber.nullaway", "nullaway", "0.14.2"));
                addJspecify(p);
            }
            ours.addChild(paths);
        }

        if (plugin == null) {
            plugin = new Plugin();
            plugin.setGroupId(MAVEN_PLUGINS);
            plugin.setArtifactId("maven-compiler-plugin");
            plugin.setConfiguration(ours);
            p.getBuild().addPlugin(plugin);
            return;
        }
        // Maven copies plugin-level configuration into each execution while building the model, which already
        // happened for the lifecycle's default-compile/default-testCompile; patch those copies as well.
        plugin.setConfiguration(mergeCompilerConfig(userCfg, ours));
        for (PluginExecution e : plugin.getExecutions()) {
            e.setConfiguration(mergeCompilerConfig((Xpp3Dom) e.getConfiguration(), ours));
        }
    }

    private Xpp3Dom compilerArgs(ResolvedRules rules) {
        Xpp3Dom args = node("compilerArgs");
        JAVAC_EXPORTS.forEach(m -> args.addChild(leaf("arg", "-J--add-exports=jdk.compiler/com.sun.tools.javac." + m + "=ALL-UNNAMED")));
        JAVAC_OPENS.forEach(m -> args.addChild(leaf("arg", "-J--add-opens=jdk.compiler/com.sun.tools.javac." + m + "=ALL-UNNAMED")));
        args.addChild(leaf("arg", "-XDcompilePolicy=simple"));
        args.addChild(leaf("arg", "--should-stop=ifError=FLOW"));
        args.addChild(leaf("arg", "-XDaddTypeAnnotationsToSymbol=true"));
        List<String> ep = new ErrorProneArgsGenerator()
                .args(rules, config).stream()
                        // The generator targets Gradle's build dir.
                        .map(a -> a.replace("/build/generated/", "/target/generated/"))
                        .toList();
        args.addChild(leaf("arg", "-Xplugin:ErrorProne " + String.join(" ", ep)));
        return args;
    }

    /**
     * Returns a merged copy: the user's DOM can be shared with the parent POM and with sibling modules, so it is
     * never mutated. Arguments and processor paths are deduplicated by value so a repeated run adds nothing.
     */
    private static Xpp3Dom mergeCompilerConfig(Xpp3Dom user, Xpp3Dom ours) {
        if (user == null) {
            return new Xpp3Dom(ours);
        }
        Xpp3Dom merged = new Xpp3Dom(user);
        for (Xpp3Dom child : ours.getChildren()) {
            Xpp3Dom existing = merged.getChild(child.getName());
            if (existing == null) {
                merged.addChild(new Xpp3Dom(child));
            } else if ("compilerArgs".equals(child.getName())) {
                for (Xpp3Dom a : child.getChildren()) {
                    if (!hasValue(existing, a.getValue())) {
                        existing.addChild(new Xpp3Dom(a));
                    }
                }
            } else if ("annotationProcessorPaths".equals(child.getName())) {
                for (Xpp3Dom a : child.getChildren()) {
                    if (!hasArtifact(existing, a.getChild("artifactId").getValue())) {
                        existing.addChild(new Xpp3Dom(a));
                    }
                }
            } else if ("fork".equals(child.getName()) && "false".equals(existing.getValue())) {
                // Error Prone runs as a javac plugin and needs the -J flags, which only a forked javac honours.
                LOG.warn("javacite: maven-compiler-plugin has fork=false; forcing fork=true because Error Prone needs it");
                existing.setValue("true");
            }
        }
        return merged;
    }

    private static boolean hasValue(Xpp3Dom parent, String value) {
        for (Xpp3Dom c : parent.getChildren()) {
            if (Objects.equals(value, c.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasArtifact(Xpp3Dom paths, String artifactId) {
        for (Xpp3Dom c : paths.getChildren()) {
            Xpp3Dom id = c.getChild("artifactId");
            if (id != null && artifactId.equals(id.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static Xpp3Dom path(String g, String a, String v) {
        return node("path", leaf("groupId", g), leaf("artifactId", a), leaf("version", v));
    }

    private static void addJspecify(MavenProject p) {
        boolean declared = p.getModel().getDependencies().stream()
                .anyMatch(d -> "org.jspecify".equals(d.getGroupId()) && "jspecify".equals(d.getArtifactId()));
        if (!declared) {
            p.getModel().addDependency(dependency("org.jspecify", "jspecify", "1.0.1", "provided"));
        }
    }

    private void spotless(MavenProject p) {
        if (!on(Tool.SPOTLESS)) {
            return;
        }
        Xpp3Dom java = node(
                "java",
                node("palantirJavaFormat", leaf("version", "2.102.0")),
                node("removeUnusedImports"),
                node("trimTrailingWhitespace"),
                node("endWithNewline"),
                // Gradle wiring adds formatAnnotations too; keep both builds formatting identically.
                node("formatAnnotations"));
        // Plugin level when we create the plugin, so `mvn spotless:apply` (default-cli) sees it too.
        bind(p, "com.diffplug.spotless", "spotless-maven-plugin", "3.10.3", List.of(), node("configuration", java), true, "verify", "check");
    }

    // failurePriority 2: priority-3 (warn) violations are reported without failing.
    private void pmd(MavenProject p) {
        if (!on(Tool.PMD)) {
            return;
        }
        boolean userPmd = find(p.getBuild(), MAVEN_PLUGINS, "maven-pmd-plugin") != null;
        Xpp3Dom cfg = node(
                "configuration",
                node("rulesets", leaf("ruleset", "${project.build.directory}/javacite/pmd.xml")),
                leaf("failOnViolation", "true"),
                leaf("failurePriority", "2"),
                leaf("printFailingErrors", "true"),
                leaf("includeTests", "true"));
        bind(p, MAVEN_PLUGINS, "maven-pmd-plugin", "3.28.0",
                List.of(
                        dependency("net.sourceforge.pmd", "pmd-core", "7.28.0", null),
                        dependency("net.sourceforge.pmd", "pmd-java", "7.28.0", null)),
                cfg, true, "verify", "check");
        warnForkedUserPlugin("maven-pmd-plugin", userPmd);
    }

    private void jacoco(MavenProject p) {
        if (!on(Tool.JACOCO)) {
            return;
        }
        Xpp3Dom limit = node(
                "limit",
                leaf("counter", "LINE"),
                leaf("value", "COVEREDRATIO"),
                leaf("minimum", String.format(Locale.ROOT, "%.2f", config.jacocoMin())));
        Xpp3Dom rules = node("rules", node("rule", leaf("element", "BUNDLE"), node("limits", limit)));
        bind(p, "org.jacoco", "jacoco-maven-plugin", "0.8.15", List.of(), node("configuration", rules), null,
                "prepare-agent", "report", "check");
    }

    private void dependencyCheck(MavenProject p) {
        if (!on(Tool.DEPENDENCY_CHECK)) {
            return;
        }
        InCheck inCheck = config.dependencyCheck().inCheck();
        boolean bound = inCheck == InCheck.ALWAYS || (inCheck == InCheck.CI && DependencyCheckOptions.isCi(env.get("CI")));
        Xpp3Dom cfg = node(
                "configuration",
                leaf("failBuildOnCVSS", String.valueOf(config.dependencyCheck().failOnCvss())),
                leaf("nvdApiKey", "${env.NVD_API_KEY}"),
                leaf("dataDirectory", "${user.home}/.javacite/nvd"));
        String id = "dependency-check-maven";
        if (bound) {
            bind(p, "org.owasp", id, "13.0.0", List.of(), cfg, "verify", "check");
        } else {
            addUnbound(p, "org.owasp", id, "13.0.0", cfg);
        }
    }

    private void bind(MavenProject p, String g, String a, String version, List<Dependency> deps, Xpp3Dom cfg, String phase, String... goals) {
        bind(p, g, a, version, deps, cfg, false, phase, goals);
    }

    /**
     * @param pluginLevel true to also put the configuration on the plugin when we create it, so command-line invocations
     *     ({@code mvn spotless:apply}; run as {@code default-cli}) and forked goals
     *     ({@code pmd:check} forks {@code pmd:pmd}) see it. A plugin the user declared keeps its own configuration
     *     and only our execution carries ours; invoke that one as {@code mvn spotless:apply@javacite}.
     */
    private void bind(MavenProject p, String g, String a, String version, List<Dependency> deps, Xpp3Dom cfg, boolean pluginLevel, String phase, String... goals) {
        Plugin plugin = find(p.getBuild(), g, a);
        if (plugin == null) {
            plugin = newPlugin(g, a, resolveVersion(p, g, a, version), pluginLevel ? cfg : null);
            deps.forEach(plugin::addDependency);
            p.getBuild().addPlugin(plugin);
        }
        if (plugin.getExecutions().stream().anyMatch(x -> EXECUTION_ID.equals(x.getId()))) {
            return;
        }
        PluginExecution e = new PluginExecution();
        e.setId(EXECUTION_ID);
        e.setPhase(phase);
        e.setGoals(new ArrayList<>(List.of(goals)));
        // Maven only merges plugin-level configuration into executions while building the model, which is over by
        // now, so the execution needs its own copy even when the plugin carries one.
        e.setConfiguration(cfg == null ? null : new Xpp3Dom(cfg));
        plugin.addExecution(e);
    }

    private void addUnbound(MavenProject p, String g, String a, String version, Xpp3Dom cfg) {
        if (find(p.getBuild(), g, a) == null) {
            p.getBuild().addPlugin(newPlugin(g, a, resolveVersion(p, g, a, version), cfg));
        }
    }

    /**
     * A plugin only in {@code pluginManagement} is not merged into a plugin added after model building, so its managed
     * version is copied here; the team's pinned version wins over ours.
     */
    private static String resolveVersion(MavenProject p, String g, String a, String pinned) {
        PluginManagement mgmt = p.getBuild().getPluginManagement();
        Plugin managed = mgmt == null ? null : find(mgmt.getPlugins(), g, a);
        if (managed == null || managed.getVersion() == null) {
            return pinned;
        }
        LOG.info("javacite: using {}:{} from pluginManagement instead of {}", a, managed.getVersion(), pinned);
        return managed.getVersion();
    }

    /** pmd:check forks a second goal that only reads plugin-level configuration. */
    private static void warnForkedUserPlugin(String a, boolean userDeclared) {
        if (userDeclared) {
            LOG.warn("javacite: {} is declared in the pom, so its forked analysis uses your plugin-level configuration, "
                    + "not javacite's generated one (see docs/maven.md)", a);
        }
    }

    private static Plugin newPlugin(String g, String a, String version, Xpp3Dom cfg) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(g);
        plugin.setArtifactId(a);
        plugin.setVersion(version);
        plugin.setConfiguration(cfg);
        return plugin;
    }

    private static Plugin find(Build build, String g, String a) {
        return find(build.getPlugins(), g, a);
    }

    private static Plugin find(List<Plugin> plugins, String g, String a) {
        return plugins.stream()
                .filter(pl -> g.equals(pl.getGroupId()) && a.equals(pl.getArtifactId()))
                .findFirst()
                .orElse(null);
    }

    private static Dependency dependency(String g, String a, String v, String scope) {
        Dependency d = new Dependency();
        d.setGroupId(g);
        d.setArtifactId(a);
        d.setVersion(v);
        d.setScope(scope);
        return d;
    }

    private static Xpp3Dom leaf(String name, String value) {
        Xpp3Dom x = new Xpp3Dom(name);
        x.setValue(value);
        return x;
    }

    private static Xpp3Dom node(String name, Xpp3Dom... children) {
        Xpp3Dom x = new Xpp3Dom(name);
        for (Xpp3Dom c : children) {
            x.addChild(c);
        }
        return x;
    }
}
