# Archive Command

You are running the `archive` command. Your job is to move completed work to history and clean up.

## Purpose

- Archive completed phases and plans
- Preserve work history for reference
- Keep active workspace clean
- Maintain long-term project memory

## When to Archive

- After completing all sessions in a phase
- After completing an entire plan
- When starting a major new direction
- During reflect mode (as part of cleanup)

## Inputs

1. `.ctx/state.md` - Current state
2. `.ctx/plan.md` - Active plan
3. `.ctx/sessions/` - Session directories
4. `.ctx/history/` - Archive destination

## Process

### 1. Identify Completed Work

```
FOR each phase in .ctx/sessions/:
  phase_complete = TRUE

  FOR each session in phase:
    IF report.md does not exist OR status != completed:
      phase_complete = FALSE
      BREAK

  IF phase_complete:
    Mark phase for archiving
```

### 2. Create Archive Entry

For each completed phase:

```
archive_name = {date}_{plan-name}_{phase-name}
archive_path = .ctx/history/{archive_name}/

Create directory structure:
{archive_path}/
├── _summary.md      # Generated summary
├── _overview.md     # Copied from phase
└── {session}/
    ├── brief.md     # Copied
    └── report.md    # Copied
```

### 3. Generate Summary

Create `_summary.md` for the archived phase:

```markdown
# Archive: {phase-name}

Archived: {date}
Plan: {plan-name}
Duration: {first-session-date} to {last-session-date}

## Sessions

| Session | Status | Key Outcome |
|---------|--------|-------------|
| {name} | completed | {one-line from report} |
| {name} | completed | {one-line from report} |

## Key Decisions
{Aggregated from session reports}

## Discoveries
{Aggregated from session reports}

## Artifacts Produced
{List of files created/modified}

## Lessons Learned
{Synthesized from reports and scratchpad}
```

### 4. Archive the Plan (if complete)

When all phases are complete:

```
1. Copy plan.md to .ctx/history/{date}_{plan-name}/plan.md

2. Create .ctx/history/{date}_{plan-name}/_final.md:

   # Completed Plan: {name}

   Completed: {date}
   Duration: {start} to {end}

   ## Summary
   {overview from plan}

   ## Phases Completed
   - Phase 1: {name} - {summary}
   - Phase 2: {name} - {summary}

   ## Total Sessions: {n}

   ## Final State
   {What was achieved}

3. Reset plan.md to "No active plan"
```

### 5. Update Cross-Reference Index

Maintain a searchable index at `.ctx/history/index.md`:

```markdown
# History Index

Last updated: {date}

## Plans

| Plan | Date | Sessions | Status |
|------|------|----------|--------|
| {plan-name} | {date} | {n} | completed |
| {plan-name} | {date} | {n} | completed |

## Decisions Log

| Date | Plan | Decision | Reasoning | Session |
|------|------|----------|-----------|---------|
| {date} | {plan} | {decision} | {why} | {link} |

## Lessons Learned

| Date | Plan | Lesson | Context |
|------|------|--------|---------|
| {date} | {plan} | {lesson} | {link to session} |

## Patterns & Solutions

| Problem | Solution | Used In |
|---------|----------|---------|
| {problem description} | {how it was solved} | {plan/session} |

## Tags

- `#auth` - {plan1}, {plan2}
- `#api` - {plan1}
- `#refactor` - {plan3}
```

### Index Update Process

```
1. Read existing .ctx/history/index.md (or create if missing)

2. FOR each session report being archived:
   - Extract "Decisions Made" → append to Decisions Log
   - Extract "Discovered" → append to Lessons Learned
   - Identify reusable patterns → add to Patterns & Solutions

3. Update Plans table with new entry

4. Auto-generate tags from:
   - Plan name keywords
   - Session types (feature, bugfix, etc.)
   - Technologies mentioned

5. Write updated index.md
```

### Searching History

During `plan` mode, search the index:
- Check Decisions Log for similar past decisions
- Check Patterns & Solutions for reusable approaches
- Check Lessons Learned to avoid past mistakes

### 6. Clean Up Sessions Directory

After archiving:

```
1. Remove archived phase directories from .ctx/sessions/
2. Keep any incomplete phases in place
3. Update state.md:
   - sessions completed: reset to 0 (for new plan)
   - last action: "archived {phase/plan}"
```

## Output Format

```
## Archive Complete

Archived:
- Phase: {name} -> history/{archive_name}/
- {n} sessions preserved

Generated:
- _summary.md with key outcomes
- Updated history/index.md

Cleaned:
- Removed {phase}/ from sessions/

Suggested: `next` to continue or `plan` for new work
```

## Archive Structure

Over time, history/ builds up:

```
.ctx/history/
  {date}_{plan-name}/
    plan.md
    _final.md
    {phase}/
      _summary.md
      _overview.md
      {session}/brief.md, report.md
```

## Partial Archive

If only some phases are complete:

1. Archive only complete phases
2. Keep plan.md active
3. Keep incomplete phases in sessions/
4. Update plan.md to mark archived phases:

```
### Phase 1: {name} [ARCHIVED]
Archived to: history/{path}

### Phase 2: {name}
{still active}
```

## Reference Archived Work

To reference past work:

```
1. Browse .ctx/history/ for past plans
2. Read _summary.md for quick overview
3. Read individual reports for details
4. Use insights to inform current work
```

## No Auto-Delete

The archive command copies, then deletes from active workspace.
Original files are never deleted without being archived first.
If archiving fails, active files remain untouched.

## Pull Request Generation

When archiving a completed plan:

### 1. Check if PR needed
- Is branch different from main? → Yes, create PR
- Are there commits to merge? → Yes, create PR

### 2. Generate PR Description

```markdown
## Summary
{Plan overview from plan.md}

## Changes
{Aggregated from session reports}

### Sessions Completed
- {session-1}: {one-line summary}
- {session-2}: {one-line summary}

## Testing
- [ ] All tests pass
- [ ] Manual testing completed

## Screenshots
{If applicable}

## Notes
{Any important context for reviewers}
```

### 3. Create PR
Using GitHub CLI if available:
```bash
gh pr create --title "{plan-name}" --body-file .ctx/pr-description.md
```

Or output instructions for manual creation.

### 4. Cleanup
- Keep branch until PR is merged
- After merge, can delete branch

## Auto-Archive Triggers

Archive runs automatically when:
1. All sessions in a phase are completed
2. All phases in a plan are completed

During reflect, check:
```
FOR each phase:
  IF all sessions completed AND not archived:
    archive phase
```

## Lightweight Archive

For single phase:
1. Move session folders to history
2. Generate summary
3. Update history index
4. Update plan.md to mark phase archived

No PR generation until full plan complete.

## Pattern Extraction

When archiving, analyze completed work for patterns:

```
FOR each completed session:
  IF report.decisions contains reusable approach:
    Add to Patterns & Solutions:
      - Problem: {what was solved}
      - Solution: {how it was solved}
      - Files: {template files if any}
      - Used In: {session reference}
      
  IF report.blockers was resolved:
    Add to Lessons Learned:
      - Lesson: {what to do differently}
      - Context: {when this applies}
      
  IF report.discoveries contains insight:
    Add to Decisions Log:
      - Decision: {what was decided}
      - Reasoning: {why}
```

For efficiency, can delegate pattern extraction to cheap model:
```
Task: Extract reusable patterns from these session reports
Context: {reports}
Output: JSON with patterns[], lessons[], decisions[]
```
