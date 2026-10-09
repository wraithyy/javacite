def log = new File(basedir, 'build.log').text
assert log.contains('maven-checkstyle-plugin') && log.contains('HideUtilityClassConstructor') : 'expected a checkstyle failure'
