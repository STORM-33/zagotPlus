# Claude Auto OS

Self-orchestrating AI workflow system built on Claude Code.

## On Conversation Start

1. Read `.ctx/state.md`
2. Check deferred actions - if any, execute first
3. Check git status - warn if uncommitted changes on wrong branch
4. Determine mode: `ready`, `working`, `blocked`
5. If mode = `ready`, check thresholds:
   - Sessions since reflect >= 3? → Add `reflect` to deferred
   - Scratchpad items >= 5? → Add `reflect` to deferred
   - Hours since reflect >= 24? → Add `reflect` to deferred
6. Load mode instructions from `.ctx/modes/{mode}.md`
7. Suggest next action based on state

## Commands

| Command | Action | Mode File |
|---------|--------|-----------|
| `plan` | Create or revise work plan | plan.md |
| `work` | Start/continue next session | work.md |
| `work {session}` | Start specific session | work.md |
| `do: {task}` | Quick task without ceremony | do.md |
| `pause` | Park current work cleanly | work.md |
| `status` | Show current state | status.md |
| `status --compact` | One-line summary | status.md |

### Automatic Operations

These run automatically based on thresholds:

| Operation | Trigger | Mode File |
|-----------|---------|-----------|
| `reflect` | 3+ sessions, 5+ scratchpad items, or 24+ hours | reflect.md |
| `validate` | On mode transitions | validate.md |
| `archive` | On phase/plan completion | archive.md |

### Manual Override

You can still run these manually:
- `reflect` - Force memory consolidation
- `validate` - Force consistency check
- `archive` - Force archiving completed work

## File Structure

```
.ctx/
  state.md          # Current state (read first)
  plan.md           # Active work plan
  journal.md        # Append-only work log
  scratchpad.md     # Cross-session notes
  modes/            # Mode instructions (plan, execute, reflect, recover, etc.)
  templates/        # Session brief templates (feature, bugfix, refactor, research)
  sessions/{phase}/{session}/  # Active work: brief.md + report.md
  memory/           # project.md + file-tree.md + modules/
  history/          # Archived completed work
```

## Configuration

### Thresholds
| Trigger | Value |
|---------|-------|
| Reflect after sessions | 3 |
| Reflect after scratchpad items | 5 |
| Reflect after hours | 24 |
| Validate on mode transition | yes |

### Git
| Setting | Value |
|---------|-------|
| Branch strategy | feature/{plan-name} |
| Commit style | conventional |
| Auto-commit on complete | yes |

### TDD
| Setting | Value |
|---------|-------|
| Default mode | encouraged |
| Require test for bugfix | yes |

### Model Delegation
| Setting | Value |
|---------|-------|
| Primary model | opus |
| Delegation target | copilot-api (GPT-5-mini) |
| Fallback | Claude Code task agent (haiku) |
| copilot-api port | 4141 |
| Auto-start copilot-api | yes |

## Core Principles

1. **State is truth** - Always read state.md first, always update it after actions
2. **Minimal context** - Load only what's needed for current task
3. **Document decisions** - Session reports capture reasoning, not just outcomes
4. **Update memory** - Reflect mode keeps project.md and file-tree.md current
5. **Recover gracefully** - Use recover mode when blocked, never leave undefined state

## Memory Files

| File | Purpose | Updated |
|------|---------|---------|
| `project.md` | Project purpose, architecture, conventions | During reflect |
| `file-tree.md` | Directory structure by module | During reflect (when structure changes) |
| `modules/*.md` | Per-module context | As needed |

## Workflow Cycle

```
plan -> next -> execute -> success -> reflect -> next (loop)
                       \-> blocked -> recover -> reflect
```

## Automatic Transitions

The system tracks progress and suggests mode transitions:

| Condition | Suggestion |
|-----------|------------|
| No active plan | `plan` |
| Plan exists, mode=idle | `next` |
| 3+ sessions since reflect | `reflect` |
| 5+ scratchpad items | `reflect` |
| Session blocked | `recover` |
| Phase complete | `reflect` → `archive` → `next` |
| All complete | `archive` → celebrate |
| External wait | `waiting` mode |

## Session Types

Use templates from `.ctx/templates/` for consistent briefs:

| Type | Use For | Key Sections |
|------|---------|--------------|
| feature | New functionality | Requirements, success criteria |
| bugfix | Bug fixes | Reproduction steps, root cause |
| refactor | Code improvements | Before/after state, rollback |
| research | Investigation | Questions to answer, time box |

## Session Complexity

Each session is assigned a complexity level that affects documentation requirements:

| Complexity | Documentation | Verification | Reflect Trigger |
|------------|---------------|--------------|-----------------|
| `low` | Fast Track (minimal report) | Basic check | Every 5 sessions |
| `medium` | Standard report | Full criteria check | Every 3 sessions |
| `high` | Detailed report + decisions | Full + edge cases | After each session |

### Complexity Guidelines

- **Low**: Single file, obvious change, no decisions (typos, config updates)
- **Medium**: 2-5 files, some decisions, clear path (add endpoint, write tests)
- **High**: Many files, architectural decisions, unknowns (new feature, complex debug)

## Progress Tracking

state.md tracks:
- **Sessions Completed** - Total finished sessions
- **Sessions Blocked** - Sessions that hit issues
- **Current Streak** - Consecutive completions (resets on block)
- **Last Reflect** - When memory was last updated

## Session Status Values

| Status | Meaning |
|--------|---------|
| pending | Not started |
| in_progress | Currently active |
| completed | Successfully finished |
| partial | Started but incomplete |
| blocked | Cannot proceed |
| waiting | External dependency |
| skipped | Intentionally bypassed |

## Recovery

When things go wrong:
1. Run `recover` to enter recovery mode
2. System diagnoses: `code_error`, `missing_context`, `unclear_requirements`, `external_dependency`, `scope_creep`, `state_corruption`
3. Assesses severity level
4. Applies resolution strategy based on severity
5. Returns to normal workflow

### Blocker Severity Levels

| Severity | Response | Example |
|----------|----------|---------|
| `critical` | Stop all work, notify user immediately | State corruption, data loss risk |
| `high` | Attempt one fix, escalate if fails | Blocks phase, no workaround |
| `medium` | Auto-resolve if possible, log | Blocks session, workaround exists |
| `low` | Fix silently, note in report | Minor inconvenience |

## Validation

Run `validate` periodically to check:
- State file integrity
- Active session consistency
- Plan structure validity
- Dependency correctness
- Report completeness
- No orphaned files

## Archiving

After completing phases/plans:
1. Run `archive` (or happens during reflect)
2. Completed work moves to `.ctx/history/`
3. Summaries generated for future reference
4. Active workspace stays clean

## Git Workflow

### Branch Strategy
```
main (protected)
  └── feature/{plan-name}
```

One branch per plan. All sessions in a plan commit to the same branch.

### Lifecycle
1. `plan` creates branch: `git checkout -b feature/{plan-name}`
2. `work` commits changes with conventional commits
3. `archive` generates PR description
4. Merge PR to main
5. Delete feature branch

### Commit Messages
Follow conventional commits:
- `feat:` new features
- `fix:` bug fixes
- `test:` test changes
- `refactor:` code restructuring
- `docs:` documentation
- `chore:` maintenance

Include session reference:
```
feat(api): add user endpoint

Session: phase-1/add-user-api
```

## TDD Workflow

### Modes
| Mode | Behavior |
|------|----------|
| `strict` | Must follow RED→GREEN→REFACTOR |
| `encouraged` | Suggest tests, don't enforce |
| `optional` | No test suggestions |

### Default by Session Type
| Type | Default TDD Mode |
|------|------------------|
| feature | encouraged |
| bugfix | strict (regression test required) |
| refactor | encouraged |
| research | optional |

### Strict Mode Cycle
1. Write failing test
2. Run test → confirm RED
3. Commit: `test: {desc} [RED]`
4. Write minimal code to pass
5. Run test → confirm GREEN
6. Commit: `feat: {desc}`
7. Refactor if needed
8. Commit: `refactor: {desc}`
9. Repeat

### TDD State Tracking

When in TDD strict mode, state.md may include:
```markdown
## TDD (active session only)
Phase: RED | GREEN | REFACTOR
Test: {current test description}
Cycle: {n} of {total}
```

## Model Delegation

### Purpose
Delegate mechanical tasks to cheaper models to save tokens.

### When to Delegate
- Boilerplate code generation
- Repetitive test creation
- Simple transformations
- Documentation generation
- Pattern application

### When NOT to Delegate
- Architectural decisions
- Complex debugging
- Security-sensitive code
- Anything requiring deep reasoning

### How It Works

1. Claude (Opus) identifies a delegatable task
2. Calls MCP tool `delegate_to_copilot`
3. MCP server routes to copilot-api (GPT-5-mini)
4. If copilot-api unavailable, falls back to Claude Code task agent (Haiku)
5. Result returned to main context

### Delegation Prompt Pattern

```
Delegate: Generate 5 unit tests for UserService.createUser()
Context: {paste relevant code}
```

### Fallback Behavior

If copilot-api fails:
1. MCP returns fallback instruction
2. Claude uses native `task` tool with `agent_type: "task"` and `model: "claude-haiku-4.5"`
3. Haiku completes the task
4. Result integrated into main work

## History & Pattern Reuse

The system learns from past work via `.ctx/history/index.md`:

### During Planning
- Check "Patterns & Solutions" for reusable approaches
- Check "Decisions Log" for consistent architectural choices
- Check "Lessons Learned" to avoid past mistakes

### During Archiving
- Decisions, discoveries, and patterns are extracted from reports
- Index is updated automatically for future reference

## Auto-Report Generation

Reports are auto-generated from execution trace to reduce overhead:

1. **During execution**: System tracks files modified, tests run, decisions made
2. **After completion**: Skeleton report is generated automatically
3. **Review step**: Human reviews and adds context (minimal for low complexity)

This reduces "management tax" while maintaining documentation quality.
