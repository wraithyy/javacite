package io.github.wraithyy.javacite.core.rules;

import io.github.wraithyy.javacite.core.config.RuleLevel;

/** A registry rule with its effective level after config overrides. */
public record ResolvedRule(Rule rule, RuleLevel level) {}
