def log = new File(basedir, 'build.log').text
assert log.contains('maven-pmd-plugin') && log.contains('CommentRequired') : 'expected a PMD CommentRequired failure'
