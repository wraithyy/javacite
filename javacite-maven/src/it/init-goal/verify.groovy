// build.log accumulates both invocations; keep only the second 'init' run.
def full = new File(basedir, 'build.log').text
def log = full.substring(full.lastIndexOf('Scanning for projects'))
['javacite.yml', 'AGENTS.md', '.mvn/extensions.xml', '.githooks/pre-commit', '.claude/settings.json'].each {
    assert new File(basedir, it).exists() : "missing $it after init"
}
def lines = log.readLines().findAll { it.contains('javacite init:') }
assert !lines.isEmpty() : 'init printed nothing'
def changed = lines.findAll { it =~ /javacite init: (created|updated)/ }
assert changed.isEmpty() : "second run must be idempotent, got: $changed"
