---
description: Run a change through the story-driven apply workflow (Obsidian-vault story graph)
---

Run an OpenSpec change through the story-driven apply workflow: decompose `tasks.md` into fully-specified stories (max 3 acceptance criteria each) with dependency chains, classify the project, write the stories into an Obsidian vault as markdown notes, then continuously poll for the next runnable story, implement it, compact context, and repeat until complete.

Load and follow the `openspec-story-driver` skill for the full workflow.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`). Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name (e.g., `/opsx-story add-auth`). If omitted, infer from conversation context, or run `openspec list --json` and let the user select if ambiguous. Optionally pass `--auto` to run the loop without pausing for confirmation between stories (interactive confirmation remains the default).

**Steps**

1. **Select the change** and announce: "Using change: <name>" (override with `/opsx-story <other>`).
2. **Verify the vault** — the vault path (`--vault` flag → `OBSIDIAN_VAULT` → `~/obsidian/obsidian`) must exist and be writable; the `generate` command errors otherwise. Print setup guidance and stop if missing.
3. **Derive the project name** — derive the canonical project name from the repository's git `origin` per the story-graph classification contract: normalize to lowercase `host/owner/repo` (preserving subgroups; strip scheme/userinfo/port/`.git`/query/fragment), fall back to `local/<repo-directory-name>` when `origin` is absent or unparseable.
4. **Resume check** — run `bb <repo>/scripts/story_driver.clj next "<name>" --project <project-name>`; "change folder not found" means a fresh run (decompose next), any `in_progress`/`done` story means resume (skip to the loop), an all-`pending` folder regenerates through the review gate.
5. **Decompose** — read `proposal.md`, `design.md`, `specs/**`, and `tasks.md`; write `stories.yaml`; run `bb <repo>/scripts/story_driver.clj generate "<name>" --project <project-name> --root <changeRoot>` to produce `stories.md` and the vault notes; show the user `stories.md` for review.
6. **Classify the project** (optional but recommended) — write the project note's classification (`repoUrl`, `techStack`, `type`) via `bb <repo>/scripts/story_driver.clj classify <project-name> [--type t] [--tech-stack a,b] [--repo-url u]` per the skill's "Classify the project" step.
7. **Loop** — poll `next` for the next runnable story, stop on a stalled run (`inProgressIds` non-empty), implement the story (reading its note via the Obsidian MCP read tool when registered, else the note file), mark it done with `set-status`, sync tasks, append state, compact context; if `--auto` was passed, continue without pausing; otherwise confirm with the user, repeat.
8. **Finish** — on `runnable: null` report completion (`counts.remaining = 0`) and suggest archive, or list the blocked stories and stop.

**Guardrails**
- Keep going through stories until done or blocked; confirmation between stories applies in interactive mode only (`--auto` skips it).
- If a story is ambiguous, pause and ask before implementing.
- The vault is never written through MCP tools; writes always go through the script. The Obsidian MCP is optional and read-only in this workflow.
- Always verify vault reachability first; never proceed silently into a broken state.
