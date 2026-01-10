# Session Brief: {session-name}

Type: bugfix
Phase: {phase-name}
Complexity: {low|medium|high}
Created: {date}

## Objective

Fix: {One clear sentence describing the bug}

## Bug Description

**Observed behavior**: {What currently happens}
**Expected behavior**: {What should happen}
**Reproduction steps**:
1. {Step 1}
2. {Step 2}
3. {Step 3}

## Impact

- Severity: {critical | high | medium | low}
- Affected users/features: {description}

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- {path/to/buggy/file}
- {path/to/related/file}

## Investigation Notes

{Any initial analysis, stack traces, or hypotheses about root cause}

## TDD

Mode: strict (regression test required)

### Regression Test
Before fixing, write a test that:
1. Reproduces the bug (fails)
2. Will pass after fix
3. Prevents future regression

### Test Command
```
{test command}
```

## Success Criteria

- [ ] Bug no longer reproduces
- [ ] Root cause is understood and documented
- [ ] Fix doesn't introduce new issues
- [ ] Tests added/updated to prevent regression

## Constraints

- {Any constraints on the fix approach}
- {Areas of code that should NOT be modified}

## Dependencies

- Requires: {none | session-name}
- Blocks: {none | session-name}
