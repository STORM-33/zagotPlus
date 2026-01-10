# Session Brief: {session-name}

Type: refactor
Phase: {phase-name}
Complexity: {low|medium|high}
Created: {date}

## Objective

Refactor: {One clear sentence describing what's being improved}

## Motivation

**Current state**: {Why the current code needs refactoring}
**Target state**: {What the code should look like after}
**Benefits**: {Why this refactor is worth doing}

## Scope

Files to modify:
- {path/to/file1}
- {path/to/file2}

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- {path/to/file/being/refactored}
- {path/to/files/that/depend/on/it}

## Refactoring Approach

{Describe the specific changes to make}

1. {Step 1}
2. {Step 2}
3. {Step 3}

## Success Criteria

- [ ] All existing tests pass
- [ ] Behavior is unchanged (pure refactor)
- [ ] Code is measurably improved (readability, performance, etc.)
- [ ] No new dependencies introduced (unless planned)

## Risks

- {Potential issues to watch for}
- {Areas that might break}

## Rollback Plan

{How to undo if something goes wrong}

## Out of Scope

- {Behavioral changes - those need a feature session}
- {Unrelated cleanup - save for another session}

## Dependencies

- Requires: {none | session-name}
- Blocks: {none | session-name}
