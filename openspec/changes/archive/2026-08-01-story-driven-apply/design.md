## Context

The repo (`my-agent-skill`) hosts OpenSpec-driven agent skills and commands under `.opencode/`. Today, implementing a change runs through `/opsx-apply`, which walks a flat checklist in `tasks.md`. As changes grow, the agent must hold proposal, design, specs, and the full task list in context at once, which causes context degradation and unverified assumptions. We want a story-driven execution layer on top of the same OpenSpec artifacts.

The change delivers an OpenCode skill + command that:
1. Decomposes an OpenSpec change into fully-specified stories (max 3 acceptance criteria each).
2. Stores the story graph (nodes + dependency edges) in a Neo4j database accessed via the official Neo4j MCP server.
3. Continuously polls for the next runnable story, implements it, compacts context, and repeats until done.

## Goals / Non-Goals

**Goals:**
- Decompose a change's `tasks.md` (with proposal/design/specs as context) into stories where each story is implementable with zero agent assumptions: it names the exact files, exact behavior, and edge cases.
- Enforce a dependency chain: a story only becomes runnable when all its dependencies are `done`.
- Persist stories and their dependency edges in Neo4j, queried exclusively through the Neo4j MCP server (no direct driver code).
- Drive a poll → implement → compact loop that keeps working context small.
- Stay compatible with OpenSpec: stories are derived from `tasks.md`, and completing a story marks the corresponding task checkboxes done so existing progress and archive validation still work.
- Provide a simple `/opsx-story` entry point plus a reusable `openspec-story-driver` skill.

**Non-Goals:**
- Modifying the OpenSpec CLI or its artifact formats.
- Replacing `/opsx-apply`; both execution paths remain available.
- Building a custom graph database client — all graph access goes through the Neo4j MCP server.
- Multi-repo / multi-change orchestration in v1 (single change per run).
- Automatic story generation without a review checkpoint.

## Decisions

### D1. Graph database: Neo4j (via official MCP server)
Stories and dependency edges live in Neo4j. Cypher is executed through the Neo4j MCP server's Cypher execution tool; the skill never shells out to a driver.
- *Alternatives:* Memgraph (in-memory, also has MCP) and ArangoDB. Neo4j is the de-facto standard, durable, has an official MCP server, and is trivial to run locally via Docker.
- *Consequence:* The skill must verify the MCP server is reachable before running; otherwise it prints setup guidance.

### D2. Graph schema
```
(:Change {name: string})
(:Story {
  id: string,            // kebab-case unique within change
  title: string,
  description: string,   // fully-specified: files, behavior, edge cases
  acceptanceCriteria: [string], // max 3
  status: string,        // pending | in_progress | done
  taskRefs: [string]     // task descriptions this story covers
})
(:Change)-[:HAS_STORY]->(:Story)
(:Story)-[:DEPENDS_ON]->(:Story)   // A DEPENDS_ON B => A requires B first
```
Readiness rule: a story is runnable when `status = pending` and none of its `DEPENDS_ON` targets are non-`done`.

### D3. Decomposition produces a seed script, not ad-hoc Cypher
The decomposition step writes a reproducible, idempotent Cypher seed file (`<changeRoot>/story-seed.cypher`) using `MERGE`. The apply loop runs the seed once (guarded so it does not re-apply over an existing run), then reads status only from the graph.
- *Why:* avoids hand-typing Cypher each session, keeps the story definition reviewable in git alongside the change artifacts, and makes re-runs deterministic.
- A human-readable `stories.md` is also written for review before seeding.

### D4. Context compaction via a local state file
After each story completes, the skill appends a compact bullet summary to `<changeRoot>/.story-state.md` (what changed, decisions made, current conventions), then instructs the model to compact context and reload state plus the next story from the graph.
- *Why:* graph nodes store structured story data, but a compact human-readable running summary is what the model needs to resume cheaply. Keeping it in the change dir ties it to the change lifecycle.
- *Alternative considered:* storing the summary as a `Change.executionSummary` property. Rejected — local file is easier to read/diff and stays aligned with artifact editing rules.

### D5. Task/checkbox synchronization
Each story carries `taskRefs` pointing at the `tasks.md` items it covers. On story completion the skill toggles those checkboxes in `tasks.md`. This keeps `/opsx-apply` progress, `openspec status`, and archive validation truthful even when implementation ran through the story path.

### D6. Loop states
- **seeding**: ensure graph seeded from `story-seed.cypher` → if a run is already in progress, resume instead.
- **polling**: query next runnable story (single Cypher query, see readiness rule).
- **implementing**: model implements the story; on completion mark `in_progress` → `done`, sync tasks, append state, compact.
- **blocked**: no runnable story but not all `done` → report remaining blocked stories and stop.
- **complete**: all stories `done` → report, suggest `/opsx-archive`.

## Risks / Trade-offs

- **Neo4j / MCP not running** → The skill checks MCP reachability first and prints exact Docker + opencode config setup steps; it never proceeds into a broken state silently.
- **Agent still makes assumptions** → Stories follow a mandatory completeness checklist during decomposition (exact file paths, exact behavior, edge cases named; acceptance criteria testable). Decomposition is reviewed before seeding.
- **Context compaction loses detail** → `.story-state.md` is the canonical summary; compaction instruction always says to reload it before polling the next story.
- **Cypher seed drifts from change artifacts** → Seed is regenerated whenever artifacts change; regeneration only allowed when no run is in progress.
- **MCP tool names vary by server version** → The skill references the Cypher execution tool by capability and documents the expected tool name; the command validates availability once at startup.
- **Stories scope-creep beyond taskRefs** → A story's `taskRefs` must exactly cover the tasks it claims; the decomposition checkpoint enforces no orphan/extra work.

## Migration Plan

No existing system to migrate. Deployment is additive:
1. Add `.opencode/skills/openspec-story-driver/SKILL.md` and `.opencode/commands/opsx-story.md`.
2. Document Neo4j + MCP server setup (Docker command, opencode MCP config snippet) in the skill.
3. No rollback needed — existing OpenSpec artifacts and `/opsx-apply` are untouched; removing the new files restores prior state.

## Open Questions

- Exact tool name(s) exposed by the Neo4j MCP server version the user configures (the skill should discover and validate at runtime rather than hardcode).
- Whether the user wants the loop to auto-run continuously in one session or step between stories with confirmation (default: step with confirmation, since story completion should be reviewable).
