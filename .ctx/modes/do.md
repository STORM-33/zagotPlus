# Quick Task Mode (`do`)

Execute a quick task without session ceremony.

## When to Use

- Single file changes
- Obvious fixes (typos, config updates)
- Tasks completable in < 5 minutes
- No architectural decisions needed

## Syntax

```
do: {description of task}
```

## Process

1. Parse task description
2. Check if copilot-api delegation is appropriate:
   - Boilerplate generation → delegate
   - Simple file edit → do directly
   - Requires reasoning → do directly
3. Execute the task
4. Commit with message: `chore: {description}`
5. Append to journal.md:
   ```
   ### {time} - do: {description}
   Files: {files changed}
   Commit: {hash}
   ```
6. Return to ready mode

## Examples

```
do: fix typo in README.md line 42
do: update version to 2.1.0 in package.json
do: add .env to .gitignore
do: remove unused import in src/utils.ts
```

## NOT for Quick Tasks

If task requires:
- Multiple files with dependencies
- Architectural decisions
- Tests
- More than 5 minutes

→ Use `work` command with a session instead.
