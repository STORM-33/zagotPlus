# Reflect Mode

Consolidate learning and update project memory.

## Triggers

Automatic (via deferred):
- 3+ sessions completed since last reflect
- 5+ scratchpad items
- 24+ hours since last reflect

Manual:
- User runs `reflect`

## Process

### 1. Gather Data
- Read session reports since last reflect
- Read journal.md recent entries
- Read scratchpad.md

### 2. Delegate Summarization (if available)
For efficiency, delegate to cheap model:
```
Task: Summarize these session reports into key decisions and discoveries
Context: {session reports}
Output: JSON with decisions[], discoveries[], patterns[]
```

If delegation unavailable, summarize manually.

### 3. Update Memory Files

**project.md** - Update if:
- Architecture changed
- New conventions established
- Key decisions made

**file-tree.md** - Update if:
- Directory structure changed
- New modules added
- Use filesystem scan, not manual

**patterns.md** - Add new patterns discovered

### 4. Process Scratchpad
- Open questions → resolve or keep
- Discoveries → move to project.md or patterns.md
- Ideas for later → keep or create future sessions
- Notes → archive or delete

### 5. Update History Index
Add to `.ctx/history/index.md`:
- Decisions made
- Lessons learned
- Patterns discovered

### 6. Clean Up
- Clear processed scratchpad items
- Update state.md:
  - last_reflect = now
  - deferred = none

## Output

```
Reflect complete.

Updated:
- project.md: {changes}
- file-tree.md: {changes}
- patterns.md: {new patterns}

Processed:
- {n} session reports
- {n} scratchpad items

Discoveries:
- {key insight 1}
- {key insight 2}
```

## When Done

Set state.md:
- mode: ready
- Reset streak to 0
- Update Last Reflect to current date

Summarize what was updated for the user.

---

## Mode Transitions

**After reflecting, suggest based on state:**

| Condition | Suggestion |
|-----------|------------|
| More sessions pending | `work` - Continue work |
| Plan complete | Celebrate, ask about next goal |
| Issues discovered during reflect | `plan` to address them |
| Project understanding changed significantly | Review plan validity |
