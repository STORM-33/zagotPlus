# Session Brief: {session-name}

Type: feature
Phase: {phase-name}
Complexity: {low|medium|high}
Created: {date}

## Objective

{One clear sentence describing what this feature does}

## Background

{Why this feature is needed - the problem it solves}

## Requirements

- [ ] {Specific requirement 1}
- [ ] {Specific requirement 2}
- [ ] {Specific requirement 3}

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- {path/to/relevant/file1}
- {path/to/relevant/file2}

## Implementation Notes

{Any specific approach, patterns to follow, or constraints}

## TDD

Mode: encouraged | strict | optional

### Test Plan
- [ ] Test: {expected behavior 1}
- [ ] Test: {expected behavior 2}
- [ ] Test: {edge case}

### Test Command
```
{npm test | pytest | go test | etc.}
```

## Success Criteria

- [ ] Feature works as described
- [ ] Tests pass (if applicable)
- [ ] No regressions in existing functionality
- [ ] Code follows project conventions

## Out of Scope

- {What this session should NOT do}
- {Related work that belongs in a different session}

## Dependencies

- Requires: {none | session-name that must complete first}
- Blocks: {none | session-name that depends on this}
