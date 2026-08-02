---
description: Run a change through the story-driven apply workflow (Neo4j story graph)
---

Run an OpenSpec change through the story-driven apply workflow: decompose `tasks.md` into fully-specified stories (max 3 acceptance criteria each) with dependency chains, classify the project, seed them into a Neo4j story graph via the Neo4j MCP server, then continuously poll for the next runnable story, implement it, compact context, and repeat until complete.

Load and follow the `openspec-story-driver` skill for the full workflow.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`). Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name (e.g., `/opsx-story add-auth`). If omitted, infer from conversation context, or run `openspec list --json` and let the user select if ambiguous.

**Steps**

1. **Select the change** and announce: "Using change: <name>" (override with `/opsx-story <other>`).
2. **Verify MCP availability** — execute `RETURN 1 AS ok` through the Neo4j MCP Cypher tool. If unreachable, print the setup guidance (Docker command + opencode MCP config) and stop.
3. **Derive the project name** — derive the canonical project name from the repository's git `origin` per the story-graph classification contract: normalize to lowercase `host/owner/repo` (preserving subgroups; strip scheme/userinfo/port/`.git`/query/fragment), fall back to `local/<repo-directory-name>` when `origin` is absent or unparseable.
4. **Decompose** — read `proposal.md`, `design.md`, `specs/**`, and `tasks.md`; write `stories.yaml`; run `bb <repo>/scripts/story_driver.clj generate "<name>" --project <project-name> --root <changeRoot>` to produce `stories.md` and `story-seed.cypher`; show the user `stories.md` for review.
5. **Classify the project** (optional but recommended) — write the `Project` node's classification (`repoUrl`, `techStack`, `type`) via the MCP Cypher tool per the skill's "Classify the project" step.
6. **Seed** — skip if a run is in progress; otherwise run `story-seed.cypher` via MCP.
7. **Loop** — poll for the next runnable story, implement it, mark it done, sync tasks, append state, compact context, confirm with the user, repeat.
8. **Finish** — report blocked stories and stop, or on completion suggest archive.

**Guardrails**
- Keep going through stories until done or blocked, with user confirmation between stories.
- If a story is ambiguous, pause and ask before implementing.
- Always verify MCP reachability first; never proceed silently into a broken state.
