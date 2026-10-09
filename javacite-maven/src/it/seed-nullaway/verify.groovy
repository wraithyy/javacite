def log = new File(basedir, 'build.log').text
assert log.contains('[NullAway]') : 'expected a NullAway error in build.log'
