## Context

The story-execution loop pauses after each completed story for user confirmation (spec requirement "Step with confirmation"; skill Phase 3 step 8; `/opsx-story` guardrail). The pause is agent-driven — it lives in the prose of `commands/opsx-story.md` and `skills/openspec-story-driver/SKILL.md`, not in `story_driver.clj` (which only does mechanical parsing/generation/sync/state). The frozen proposal adds an opt-in `--auto` flag so the loop continues uninterrupted while keeping interactive mode as the default.

## Goals / Non-Goals

**Goals:**
- `--auto` on `/opsx-story` runs the Phase 3 loop without inter-story confirmation.
- Interactive mode stays byte-for-byte the same behavior as today.
- Auto mode stops at the same points interactive mode stops (Phase 1 `stories.md` approval, ambiguous story, MCP unreachable, blocked run, completion), only skipping the confirmation pause.
- Flag documented in the command and skill (input line, loop step, guardrails); guardrail wording updated.

**Non-Goals:**
- No changes to `story_driver.clj`, graph model, seeding, task sync, state append, or context compaction.
- No changes to any other capability or spec.
- No persistent config or env-var mechanism (a single opt-in flag per invocation).

## Decisions

### D1: `--auto` is a command-line flag on `/opsx-story`

`/opsx-story --auto <change>` (flag before or after the change name). Recognition of the flag is prose-level: the agent reads the command text and applies auto mode accordingly; harness-specific argument parsing is out of scope (see D3). No env var, no config file: the confirmation is an interactive-session concern, and a per-invocation flag is the smallest surface that cannot leak into unrelated runs.

*Alternatives considered:* env var `OPSX_STORY_AUTO=1` (rejected: implicit, easy to forget and accidentally leave on); config-file setting (rejected: global state affects all sessions); removing confirmation entirely (rejected: user chose opt-in).

### D2: The loop prose owns the mode switch

In `commands/opsx-story.md`:
- Input line gains: "Optionally pass `--auto` to run the loop without pausing for confirmation between stories."
- Step "Loop" text: "confirm with the user, repeat" becomes "if `--auto` was passed, continue without pausing; otherwise confirm with the user, repeat."
- Guardrail "Keep going through stories until done or blocked, with user confirmation between stories" becomes "...until done or blocked; confirmation between stories applies in interactive mode only (`--auto` skips it)."

In `skills/openspec-story-driver/SKILL.md`:
- Input line gains: "Optionally pass `--auto` to run the loop without pausing for confirmation between stories (interactive confirmation remains the default)."
- Phase 3 step 8 ("Confirm with the user") gains: "If the run was started with `--auto`, skip the confirmation and continue to the next story automatically; all other stops (Phase 1 `stories.md` approval, ambiguity, MCP failure, blocked, complete) still apply."
- Guardrails: add a bullet stating "Confirmation between stories applies in interactive mode only; `--auto` skips it (the loop still stops on ambiguity, MCP failure, blocked runs, and completion)."

*Alternatives considered:* a helper script flag that gates a prompt (rejected: the pause is agent prose, not a script call; there is no script to gate).

### D3: No new validation

`--auto` needs no validation of its own beyond being recognized as the opt-in token in the command prose: the loop mode is decided by the agent reading the command, not by a parser. Harness-specific argument parsing (including unknown-flag rejection, if any) is out of scope — the command parser is harness-owned and may differ between harnesses.

## Risks / Trade-offs

- [User runs --auto on a change with ambiguous stories] → ambiguity stops are NOT bypassed (D2 wording); the loop still pauses on any ambiguous story. Mitigation documented in the command guardrail.
- [--auto becomes the habit, losing review value] → interactive remains the default; the flag must be typed each time, so accidental use is unlikely.
- [Skill/command prose drifts from the spec] → delta spec updated in the same change; the task list verifies the prose matches.

## Migration Plan

1. Update `commands/opsx-story.md`: input line, loop step, guardrail.
2. Update `skills/openspec-story-driver/SKILL.md`: input line, Phase 3 step 8, guardrails bullet.
3. Verify: grep both files for `--auto` wording; confirm interactive behavior text unchanged except the documented additions.
4. Update delta spec (`story-execution`) per this design.

## Open Questions

- None.
