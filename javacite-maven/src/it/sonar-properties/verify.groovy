def file = new File(basedir, 'target/javacite/sonar-project.properties')
assert file.exists() : 'sonar-project.properties was not written'
def props = new Properties()
file.withReader { props.load(it) }
assert props['sonar.projectKey'] == 'com.example:it-project'
assert props['sonar.sources'] == 'src/main/java'
assert props['sonar.java.binaries'] == 'target/classes'
assert props['sonar.java.pmd.reportPaths'] == 'target/pmd.xml'
assert props['sonar.coverage.jacoco.xmlReportPaths'] == 'target/site/jacoco/jacoco.xml'
assert !props.containsKey('sonar.tests') || props['sonar.tests'] == '' : 'no test sources exist'
