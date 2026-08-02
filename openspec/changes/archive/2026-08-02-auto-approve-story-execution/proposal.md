## Why

The story-execution loop pauses for user confirmation after every story, which makes multi-story runs (8+ stories, as seen in recent changes) require constant babysitting. For trusted, well-specified stories the pause adds friction without value; users want an opt-in mode where the loop continues automatically until blocked or complete.

## What Changes

- Add an opt-in auto-approve mode to `/opsx-story` via a `--auto` flag (e.g. `/opsx-story --auto <change>`), enabling uninterrupted execution of the story loop.
- In auto mode, the loop SHALL NOT pause for confirmation between stories. `--auto` changes ONLY the inter-story confirmation in Phase 3: it does NOT bypass any other stop — Phase 1 `stories.md` approval, ambiguous-story pauses, MCP-unreachable stops, blocked runs (no runnable story / dependency deadlock), and completion all behave exactly as in interactive mode, with the same status reporting.
- Interactive mode (no `--auto`) remains the default and behaves exactly as today: confirmation after each story.
- The mechanism is documented in `commands/opsx-story.md` and `skills/openspec-story-driver/SKILL.md`; guardrail wording updated to reflect that confirmation is interactive-only.
- No changes to graph model, seeding, task sync, state append, or context compaction.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `story-execution`: the "Step with confirmation" requirement gains an opt-in auto-approve mode — interactive confirmation remains the default; with auto mode enabled the loop proceeds without pausing.

## Impact

- `openspec/changes/auto-approve-story-execution/specs/story-execution/spec.md` — delta spec for the modified requirement.
- `commands/opsx-story.md` — `--auto` flag in the input line and step text; guardrail updated.
- `skills/openspec-story-driver/SKILL.md` — Phase 3 step 8 documents `--auto`; confirmation described as interactive-only.
- No script changes (`story_driver.clj` is not involved in the confirmation loop — it is agent-driven in the skill/command).
- No changes to any other capability or spec.
- No new dependencies.
