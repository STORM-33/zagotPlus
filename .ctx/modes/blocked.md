# Blocked Mode

System is blocked and needs intervention.

## Entry Conditions

- Session cannot proceed
- Critical error encountered
- External dependency missing
- State corruption detected

## Process

1. Read state.md blocker description
2. Categorize:
   - `code_error`: Fix and retry
   - `missing_context`: Update brief, add files
   - `unclear_requirements`: Ask user
   - `external_dependency`: Wait or workaround
   - `scope_creep`: Split session
   - `state_corruption`: Reset state

3. Assess severity and respond:

| Severity | Response |
|----------|----------|
| critical | Stop all work, notify user, await guidance |
| high | One fix attempt, then escalate |
| medium | Try to resolve, log outcome |
| low | Auto-resolve, note in report |

4. Execute resolution
5. Validate state consistency
6. Return to ready or working mode

## Quick Fixes

### Code Error
```
1. Read error message
2. Identify root cause
3. Fix the code
4. Run tests
5. If passes, continue session
```

### Missing Context
```
1. Identify what's missing
2. Update brief.md with required files
3. Load missing context
4. Continue session
```

### State Corruption
```
1. Check git status for truth
2. Scan session directories
3. Rebuild state.md from filesystem
4. Clear invalid references
```

### External Dependency
```
1. Document what we're waiting for
2. Check if workaround exists
3. If workaround: apply it, continue
4. If not: set mode=blocked, ask user
```

## Escalation

If cannot resolve:
1. Document issue in state.md blockers
2. Document in session report
3. Set mode: blocked
4. Ask user for guidance with clear options

## Wait Tracking

For external waits, track in scratchpad:

```markdown
## Waiting On
- [ ] {dependency} for {session} (since {date})
  - Impact: blocks {n} sessions
  - Last checked: {date}
```

## Timeout Guidance

| Duration | Suggestion |
|----------|------------|
| < 1 day | Normal, check again soon |
| 1-3 days | Consider workaround or parallel work |
| 3-7 days | Escalate or revise plan |
| > 7 days | Likely should abandon or major replanning |

## Resolution Paths

### Resolved
```
1. Update state.md:
   - mode: working (or ready)
   - blockers: none
2. Continue with session
```

### Workaround
```
1. Document workaround approach
2. Update plan/brief if needed
3. Set mode back to ready or working
4. Note in scratchpad
```

### Abandon
```
1. Mark session as blocked (permanent) in report
2. Update plan.md
3. Set state.md:
   - mode: ready
   - active: none
4. Suggest next action
```

## Output

Update these files:
- `.ctx/state.md` - Clear blocker, set appropriate mode
- `.ctx/sessions/{phase}/{session}/report.md` - Document resolution
- `.ctx/scratchpad.md` - Note issue for learning
- `.ctx/plan.md` - If sessions were split or reordered

Never leave the system in an undefined state.
