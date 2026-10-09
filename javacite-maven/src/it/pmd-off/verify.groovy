def log = new File(basedir, 'build.log').text
assert log.contains('checkstyle:3.6.0:check') : 'checkstyle should still run'
assert !log.contains('pmd:') : 'pmd: off must not bind maven-pmd-plugin'
