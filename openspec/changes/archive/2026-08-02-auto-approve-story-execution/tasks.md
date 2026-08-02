## 1. Update the opsx-story command

- [x] 1.1 Add `--auto` to the command's input line: "Optionally pass `--auto` to run the loop without pausing for confirmation between stories (interactive confirmation remains the default)"
- [x] 1.2 Update the Loop step: "if `--auto` was passed, continue without pausing; otherwise confirm with the user, repeat"
- [x] 1.3 Update the guardrail: "Keep going through stories until done or blocked; confirmation between stories applies in interactive mode only (`--auto` skips it)"

## 2. Update the story-driver skill

- [x] 2.1 Add `--auto` to the skill's input line with the same wording as the command (interactive remains default)
- [x] 2.2 Update Phase 3 step 8: "If the run was started with `--auto`, skip the confirmation and continue to the next story automatically; all other stops (Phase 1 stories.md approval, ambiguity, MCP failure, blocked, complete) still apply"
- [x] 2.3 Add a guardrails bullet: "Confirmation between stories applies in interactive mode only; `--auto` skips it (the loop still stops on ambiguity, MCP failure, blocked runs, and completion)"

## 3. Verification

- [x] 3.1 Grep `commands/opsx-story.md` and `skills/openspec-story-driver/SKILL.md` for `--auto` — present in input line, loop/step text, and guardrails in both files
- [x] 3.2 Confirm interactive-mode wording is unchanged except the documented additions (no confirmation behavior removed)
- [x] 3.3 Confirm the wording in both files preserves Phase 1 stories.md approval, ambiguity stops, MCP-unreachable stops, blocked runs, and completion under `--auto`
