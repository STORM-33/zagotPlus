# Work Mode

Start or continue work on sessions.

## Command

```
work              # Continue current or start next session
work {session}    # Start specific session
```

## Process

### If No Active Session

1. Scan plan for next available session:
   - Priority: partial > pending (dependencies met)
   - Order: by phase, then by session number
2. Check dependencies are satisfied
3. If no session available:
   - All complete? → Suggest `reflect` then celebrate
   - Blocked? → List blocked sessions and blockers
4. Create/checkout git branch if needed
5. Load session brief
6. Set state: mode=working, active={session}
7. Begin execution

### Git Branch Management

1. Determine expected branch:
   - If starting new plan: `feature/{plan-name}`
   - If continuing session: current plan's branch
2. Check current branch: `git branch --show-current`
3. If mismatch:
   - If expected branch exists: `git checkout {branch}`
   - If not: `git checkout -b {branch}`
4. If uncommitted changes on wrong branch:
   - Warn user
   - Offer to stash or commit first

### If Active Session Exists

1. Load session brief and any partial report
2. Continue from where left off

### Pre-Execution Check

Before starting work:

```
1. Read session brief
2. Search history for similar sessions
3. Check if they were blocked and why

IF high_block_rate for similar sessions:
  WARN user:
  "⚠️ Similar sessions have blocked {n}% of the time.
   Common causes: {cause1}, {cause2}
   
   Verify before starting:
   - [ ] {check1}
   - [ ] {check2}"
   
  Ask: "Proceed anyway? (y/n)"
```

### During Execution

1. Check brief for TDD mode:
   - If `strict`: Follow RED→GREEN→REFACTOR cycle
   - If `encouraged`: Suggest tests, don't enforce
   - If `optional`: Skip test suggestions
2. Do the work
3. For mechanical sub-tasks, consider delegation (see Delegation section)
4. Commit changes with conventional messages
5. Verify against success criteria

### On Completion

1. Generate report (auto-fill from git diff + test results)
2. Commit report
3. Update state.md:
   - mode: ready
   - active: none
   - Stats: increment completed, update streak
4. Append to journal.md
5. Check thresholds → set deferred if needed
6. Suggest next action

### Threshold Check

After updating stats:

```
sessions_since_reflect = stats.completed - last_reflect_session_count
scratchpad_items = count items in scratchpad.md
hours_since_reflect = now - stats.last_reflect

IF sessions_since_reflect >= 3 THEN
  state.deferred = "reflect: 3+ sessions completed"
ELSE IF scratchpad_items >= 5 THEN
  state.deferred = "reflect: scratchpad overflow"
ELSE IF hours_since_reflect >= 24 THEN
  state.deferred = "reflect: 24+ hours"
END
```

Notify user:
```
Session complete. Reflect recommended before next session.
Reason: {reason}
```

### On Block

1. Document blocker in report
2. Assess severity:
   - `critical`: Stop, notify user immediately
   - `high`: Attempt one fix, escalate if fails
   - `medium`: Log, suggest workaround
   - `low`: Note and continue if possible
3. If unresolvable:
   - Set state: mode=blocked
   - Ask user for guidance

## Delegation

For mechanical sub-tasks during execution:

### When to Delegate
- Generating boilerplate (tests, types, repetitive code)
- Simple transformations
- Documentation generation
- Pattern application

### How to Delegate
1. Check if copilot-api is running (localhost:4141)
2. If not running, auto-start: `npx copilot-api@latest start --port 4141`
3. Call MCP tool `delegate_to_copilot` with task
4. If fails, fallback to Claude Code task agent (haiku)
5. Review result before using

### When NOT to Delegate
- Architectural decisions
- Complex debugging
- Security-sensitive code
- Anything requiring deep reasoning

## Commit Conventions

Use conventional commits format:

```
{type}({scope}): {description}

{body - optional}

Session: {session-name}
```

### Types
| Type | Use For |
|------|---------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code restructuring |
| `test` | Adding/updating tests |
| `docs` | Documentation |
| `chore` | Maintenance, config |

### Examples
```
feat(auth): add login endpoint

Implements POST /api/login with JWT token response.

Session: phase-1/add-auth-endpoints
```

```
test(auth): add login validation tests [RED]

Session: phase-1/add-auth-endpoints
```

### Commit Frequency
- TDD: Commit after each RED, GREEN, REFACTOR
- Normal: Commit after each logical change
- Never: Leave uncommitted work at session end

## TDD Workflow (when mode = strict)

```
┌─────────┐    ┌─────────┐    ┌─────────┐
│   RED   │───►│  GREEN  │───►│REFACTOR │──┐
│         │    │         │    │         │  │
│ Write   │    │ Write   │    │ Clean   │  │
│ failing │    │ minimal │    │ up code │  │
│ test    │    │ code    │    │         │  │
└─────────┘    └─────────┘    └─────────┘  │
     ▲                                      │
     └──────────────────────────────────────┘
                  next test
```

1. Write test that fails
2. Run test → confirm RED
3. Commit: `test: {description} [RED]`
4. Write minimal code to pass
5. Run test → confirm GREEN
6. Commit: `feat: {description}`
7. Refactor if needed
8. Run test → confirm still GREEN
9. Commit: `refactor: {description}` (if changed)
10. Repeat for next requirement

## Pause Command

```
pause             # Park current work cleanly
pause {reason}    # Park with note
```

### Process

1. Stage all changes (don't commit incomplete work)
2. Create stash: `git stash push -m "auto-os: {session} paused"`
3. Update state.md:
   - mode: ready
   - active: none
   - Add to deferred: `resume {session}: paused by user`
4. Note in journal: `### {time} - paused: {session}`

### Resume

When `work` is called and deferred has paused session:
1. Prompt: "Resume paused session {session}? (y/n)"
2. If yes: `git stash pop`, continue session
3. If no: Clear deferred, proceed normally
