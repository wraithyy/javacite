def log = new File(basedir, 'build.log').text
assert log.contains('spotless:3.10.3:check') : 'spotless should still run'
assert !log.contains('pmd:') : 'pmd: off must not bind maven-pmd-plugin'
