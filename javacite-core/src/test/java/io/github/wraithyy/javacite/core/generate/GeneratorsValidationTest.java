package io.github.wraithyy.javacite.core.generate;

import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import io.github.wraithyy.javacite.core.config.JavaciteConfig;
import io.github.wraithyy.javacite.core.config.Tool;
import io.github.wraithyy.javacite.core.rules.ResolvedRules;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

/** Loads the generated configs with the real tools so wrong native keys in the registry fail here. */
class GeneratorsValidationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void checkstyleConfigLoadsAndCheckerInstantiates(boolean overrides) throws Exception {
        ResolvedRules rules = ResolvedRules.of(overrides ? GeneratorsGoldenTest.overrides() : GeneratorsGoldenTest.defaults());
        String xml = new CheckstyleXmlGenerator().generate(rules);
        Configuration cfg = ConfigurationLoader.loadConfiguration(
                new InputSource(new StringReader(xml)), new PropertiesExpander(new Properties()),
                ConfigurationLoader.IgnoredModulesOptions.OMIT);
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(cfg);
        } finally {
            checker.destroy();
        }
        assertThat(xml.split("<module name=\"", -1).length - 1)
                .isEqualTo(rules.enabledFor(Tool.CHECKSTYLE).size() + 5);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pmdRulesetLoadsEveryRule(boolean overrides) throws Exception {
        ResolvedRules rules = ResolvedRules.of(overrides ? GeneratorsGoldenTest.overrides() : GeneratorsGoldenTest.defaults());
        Path file = java.nio.file.Files.createTempFile("javacite-pmd", ".xml");
        java.nio.file.Files.writeString(file, new PmdRulesetGenerator().generate(rules));
        RuleSetLoader loader = new RuleSetLoader().warnDeprecated(true);
        RuleSet set = loader.loadFromResource(file.toString());
        assertThat(set.getRules()).hasSize(rules.enabledFor(Tool.PMD).size());
    }

    @Test
    void errorProneCheckNamesAreValid() throws Exception {
        JavaciteConfig config = GeneratorsGoldenTest.defaults();
        List<String> args = new ErrorProneArgsGenerator().args(ResolvedRules.of(config), config);
        String processorPath = System.getProperty("errorprone.processorpath");
        List<URL> urls = new ArrayList<>();
        for (String p : processorPath.split(File.pathSeparator)) {
            urls.add(new File(p).toURI().toURL());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        StringWriter out = new StringWriter();
        JavaFileObject src = new SimpleJavaFileObject(URI.create("string:///Hello.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "public class Hello { int f() { return 1; } }";
            }
        };
        List<String> opts = new ArrayList<>(List.of(
                "-proc:none", "-d", Path.of(System.getProperty("java.io.tmpdir")).toString(),
                "-XDcompilePolicy=simple", "-XDaddTypeAnnotationsToSymbol=true", "--should-stop=ifError=FLOW",
                "-processorpath", processorPath));
        opts.add("-Xplugin:ErrorProne " + String.join(" ", args));
        // Plugin classes load from -processorpath; the URLs above only assert the jars exist.
        assertThat(urls).allSatisfy(u -> assertThat(new File(u.getPath())).exists());
        Boolean ok = javac.getTask(out, null, null, opts, null, List.of(src)).call();
        assertThat(out.toString()).doesNotContain("not a valid checker name");
        assertThat(ok).as(out.toString()).isTrue();
    }
}
