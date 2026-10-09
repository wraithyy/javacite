package io.github.wraithyy.javacite.maven;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wraithyy.javacite.core.config.ConfigLoader;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

class ExecutionInjectorTest {

    private static final String PMD = "maven-pmd-plugin";

    private static MavenProject project() {
        return new MavenProject(new Model());
    }

    private static Plugin plugin(MavenProject p, String artifactId) {
        return p.getBuild().getPlugins().stream()
                .filter(pl -> pl.getArtifactId().equals(artifactId))
                .findFirst()
                .orElse(null);
    }

    private static void inject(MavenProject p, JavaciteConfig c, Map<String, String> env) {
        new ExecutionInjector(c, env, "0.1.0-SNAPSHOT").inject(p, false);
    }

    @Test
    void absentPluginIsAddedWithExecutionAndConfig() {
        MavenProject p = project();
        inject(p, ConfigLoader.defaults(), Map.of());

        Plugin pmd = plugin(p, PMD);
        assertThat(pmd).isNotNull();
        assertThat(pmd.getVersion()).isEqualTo("3.28.0");
        PluginExecution e = pmd.getExecutions().get(0);
        assertThat(e.getGoals()).containsExactly("check");
        // pmd:check forks pmd:pmd, which only sees plugin-level configuration.
        assertThat(((Xpp3Dom) pmd.getConfiguration()).getChild("failurePriority").getValue()).isEqualTo("2");
        assertThat(((Xpp3Dom) e.getConfiguration()).getChild("failurePriority")).isNotNull();
    }

    @Test
    void existingPluginKeepsUserConfigAndGainsExecution() {
        MavenProject p = project();
        Plugin user = new Plugin();
        user.setGroupId("org.apache.maven.plugins");
        user.setArtifactId(PMD);
        user.setVersion("3.0.0");
        Xpp3Dom userCfg = new Xpp3Dom("configuration");
        userCfg.addChild(new Xpp3Dom("custom"));
        user.setConfiguration(userCfg);
        p.getBuild().addPlugin(user);

        inject(p, ConfigLoader.defaults(), Map.of());

        assertThat(p.getBuild().getPlugins().stream().filter(pl -> pl.getArtifactId().equals(PMD))).hasSize(1);
        assertThat(user.getVersion()).isEqualTo("3.0.0");
        assertThat(((Xpp3Dom) user.getConfiguration()).getChildCount()).isEqualTo(1);
        assertThat(user.getExecutions()).extracting(PluginExecution::getId).containsExactly("javacite");
        assertThat(((Xpp3Dom) user.getExecutions().get(0).getConfiguration()).getChild("failurePriority")).isNotNull();
    }

    @Test
    void toolOffMeansNoPlugin() {
        MavenProject p = project();
        inject(p, ConfigLoader.load("tools:\n  pmd: off\n"), Map.of());

        assertThat(plugin(p, PMD)).isNull();
        assertThat(plugin(p, "maven-checkstyle-plugin")).isNotNull();
    }

    @Test
    void compilerArgsAreMergedIntoUserConfig() {
        MavenProject p = project();
        Plugin user = new Plugin();
        user.setGroupId("org.apache.maven.plugins");
        user.setArtifactId("maven-compiler-plugin");
        Xpp3Dom cfg = new Xpp3Dom("configuration");
        Xpp3Dom args = new Xpp3Dom("compilerArgs");
        Xpp3Dom mine = new Xpp3Dom("arg");
        mine.setValue("-parameters");
        args.addChild(mine);
        cfg.addChild(args);
        user.setConfiguration(cfg);
        p.getBuild().addPlugin(user);

        inject(p, ConfigLoader.defaults(), Map.of());

        Xpp3Dom merged = ((Xpp3Dom) user.getConfiguration()).getChild("compilerArgs");
        assertThat(merged.getChild(0).getValue()).isEqualTo("-parameters");
        // user's -parameters + 8 exports + 2 opens + 3 javac flags + the Error Prone plugin
        assertThat(merged.getChildCount()).isEqualTo(15);
        assertThat(((Xpp3Dom) user.getConfiguration()).getChild("annotationProcessorPaths").getChildCount())
                .isEqualTo(2);
        assertThat(p.getModel().getDependencies()).anyMatch(d -> d.getArtifactId().equals("jspecify"));
    }

    @Test
    void dependencyCheckBindsOnlyInCi() {
        MavenProject local = project();
        inject(local, ConfigLoader.defaults(), Map.of());
        assertThat(plugin(local, "dependency-check-maven").getExecutions()).isEmpty();

        MavenProject ci = project();
        inject(ci, ConfigLoader.defaults(), Map.of("CI", "true"));
        assertThat(plugin(ci, "dependency-check-maven").getExecutions()).hasSize(1);
    }

    @Test
    void injectingTwiceDoesNotDuplicate() {
        MavenProject p = project();
        inject(p, ConfigLoader.defaults(), Map.of());
        inject(p, ConfigLoader.defaults(), Map.of());
        assertThat(plugin(p, PMD).getExecutions()).hasSize(1);
    }

    private static Plugin userCompiler(Xpp3Dom cfg) {
        Plugin user = new Plugin();
        user.setGroupId("org.apache.maven.plugins");
        user.setArtifactId("maven-compiler-plugin");
        user.setConfiguration(cfg);
        return user;
    }

    @Test
    void modulesSharingOneParentConfigurationDoNotMutateOrDuplicate() {
        Xpp3Dom shared = new Xpp3Dom("configuration");
        Xpp3Dom args = new Xpp3Dom("compilerArgs");
        Xpp3Dom mine = new Xpp3Dom("arg");
        mine.setValue("-parameters");
        args.addChild(mine);
        shared.addChild(args);
        Xpp3Dom fork = new Xpp3Dom("fork");
        fork.setValue("false");
        shared.addChild(fork);

        MavenProject a = project();
        a.getBuild().addPlugin(userCompiler(shared));
        MavenProject b = project();
        b.getBuild().addPlugin(userCompiler(shared));
        inject(a, ConfigLoader.defaults(), Map.of());
        inject(b, ConfigLoader.defaults(), Map.of());

        assertThat(shared.getChild("compilerArgs").getChildCount()).isEqualTo(1);
        assertThat(shared.getChild("fork").getValue()).isEqualTo("false");
        for (MavenProject p : new MavenProject[] {a, b}) {
            Xpp3Dom cfg = (Xpp3Dom) plugin(p, "maven-compiler-plugin").getConfiguration();
            assertThat(cfg.getChild("compilerArgs").getChildCount()).isEqualTo(15);
            assertThat(cfg.getChild("annotationProcessorPaths").getChildCount()).isEqualTo(2);
            assertThat(cfg.getChild("fork").getValue()).isEqualTo("true");
        }
    }

    @Test
    void releaseIsInjectedUnlessUserSetsIt() {
        MavenProject plain = project();
        inject(plain, ConfigLoader.load("java: 21\ntools:\n  errorprone: off\n  nullaway: off\n"), Map.of());
        Xpp3Dom cfg = (Xpp3Dom) plugin(plain, "maven-compiler-plugin").getConfiguration();
        assertThat(cfg.getChild("release").getValue()).isEqualTo("21");
        assertThat(cfg.getChild("compilerArgs")).isNull();

        MavenProject withProperty = project();
        withProperty.getProperties().setProperty("maven.compiler.release", "17");
        inject(withProperty, ConfigLoader.defaults(), Map.of());
        assertThat(((Xpp3Dom) plugin(withProperty, "maven-compiler-plugin").getConfiguration()).getChild("release"))
                .isNull();
    }

    @Test
    void createdSpotlessPluginCarriesConfigAtPluginLevel() {
        MavenProject p = project();
        inject(p, ConfigLoader.defaults(), Map.of());
        Plugin spotless = plugin(p, "spotless-maven-plugin");
        Xpp3Dom java = ((Xpp3Dom) spotless.getConfiguration()).getChild("java");
        assertThat(java.getChild("formatAnnotations")).isNotNull();
        assertThat(((Xpp3Dom) spotless.getExecutions().get(0).getConfiguration()).getChild("java")).isNotNull();
    }

    @Test
    void managedPluginVersionIsReused() {
        MavenProject p = project();
        Plugin managed = new Plugin();
        managed.setGroupId("org.apache.maven.plugins");
        managed.setArtifactId(PMD);
        managed.setVersion("3.99.0");
        PluginManagement mgmt = new PluginManagement();
        mgmt.addPlugin(managed);
        p.getBuild().setPluginManagement(mgmt);
        inject(p, ConfigLoader.defaults(), Map.of());
        assertThat(plugin(p, PMD).getVersion()).isEqualTo("3.99.0");
    }
}
