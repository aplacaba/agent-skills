---
name: openspec-story-driver
description: Drive OpenSpec change implementation through a story graph in Neo4j. Decompose a change into fully-specified stories (max 3 acceptance criteria each), seed them into Neo4j via the Neo4j MCP server, then continuously poll for the next runnable story, implement it, compact context, and repeat until the change is complete.
license: MIT
compatibility: Requires openspec CLI and the Neo4j MCP server configured in opencode.
metadata:
  author: openspec
  version: "1.0"
---

# Story-Driven Apply

Run an OpenSpec change through a story graph stored in Neo4j, accessed via the official Neo4j MCP server. The workflow decomposes a change's `tasks.md` into stories with at most 3 acceptance criteria and explicit dependency chains, then executes them one at a time with context compaction between stories.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`). Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name (e.g., `/opsx-story add-auth`). If omitted, infer from conversation or `openspec list --json`. Announce "Using change: <name>".

## Prerequisites

### Neo4j (Docker)
```bash
docker run -d --name story-neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/storypass \
  neo4j:latest
```

### Neo4j MCP server in opencode config
Add to your opencode config (e.g. `opencode.json` or `~/.config/opencode/opencode.json`):

```jsonc
{
  "mcp": {
    "neo4j": {
      "type": "local",
      "command": [
        "npx",
        "-y",
        "@neo4j/mcp-server",
        "--uri", "bolt://localhost:7687",
        "--database", "neo4j",
        "--username", "neo4j",
        "--password", "storypass"
      ]
    }
  }
}
```

Restart opencode after adding the MCP server. All graph access in this skill goes through the `neo4j` MCP server's Cypher execution tool — never shell out to a driver.

### Helper script
`story-driver.py` (in the repo scripts/ directory) performs the mechanical parts: parsing tasks, validating the story definition, generating `stories.md` and `story-seed.cypher`, toggling task checkboxes, and appending state. Run it as `python3 <repo>/scripts/story-driver.py <command> ...`.

## Workflow

### Phase 0 — Verify MCP availability

Before anything else, confirm the Neo4j MCP server is reachable by executing a trivial Cypher query through the MCP Cypher tool:

```cypher
RETURN 1 AS ok
```

- **If unreachable:** print setup guidance (the Docker command and opencode MCP config snippet above), then STOP. Do not modify any files.

### Phase 1 — Decompose the change into stories

Read the change's context: `proposal.md`, `design.md`, `specs/**`, and `tasks.md`. Then decompose `tasks.md` into a story definition.

1. Parse tasks so none are missed:
   ```bash
   openspec status --change "<name>" --json
   python3 <repo>/scripts/story-driver.py parse-tasks openspec/changes/<name>/tasks.md
   ```
2. Write `<changeRoot>/stories.yaml` following the **Zero-Assumption Story Template** below. Group related tasks into stories; each story maps to the task descriptions it covers via `taskRefs`. Combined, `taskRefs` must cover every task exactly once (no orphans, no extras).
3. Every story MUST satisfy the completeness checklist:
   - Exact file paths to create/modify (relative to repo root).
   - Exact behavior to implement, stated so an agent needs no judgment call.
   - Edge cases named explicitly.
   - At most 3 acceptance criteria, each objectively checkable.
4. Enforce dependency chains: a story may `dependsOn` stories that must finish first. Never create cycles.
5. If a task set needs more than 3 acceptance criteria, split it into multiple stories.
6. Validate and generate:
   ```bash
   python3 <repo>/scripts/story-driver.py generate "<name>" --root <changeRoot>
   ```
   This writes `stories.md` (human-readable review copy) and `story-seed.cypher` (idempotent MERGE seed). Fix any validation errors it reports.
7. Show the user `stories.md` for review before seeding. Pause for approval.

**Zero-Assumption Story Template (use in `stories.yaml`):**

```yaml
change: <change-name>
stories:
  - id: <kebab-case-unique-id>
    title: <short title>
    description: |
      WHAT: <exact behavior to implement>
      FILES:
        - create: <exact path>
        - modify: <exact path>
      EDGE CASES:
        - <edge case 1>
        - <edge case 2>
      CONTEXT: <pointer to relevant design/spec section if needed>
    acceptanceCriteria:
      - <criterion 1>          # max 3, objectively checkable
      - <criterion 2>
    dependsOn:
      - <story-id-that-must-finish-first>
    taskRefs:
      - "<exact task description text from tasks.md>"
```

### Phase 2 — Seed the story graph

Decide whether to seed or resume:

```cypher
// Seed only if no run is in progress
MATCH (c:Change {name: "<name>"})-[:HAS_STORY]->(s:Story)
WHERE s.status IN ["in_progress", "done"]
RETURN count(s) > 0 AS runInProgress
```

- **If `runInProgress` is true:** skip seeding, keep the existing graph state, and go to Phase 3.
- **If false:** execute `story-seed.cypher` through the MCP Cypher tool. It is idempotent (`MERGE`), so re-running produces no duplicates.

### Phase 3 — Execution loop

Repeat until complete or blocked:

1. **Poll** for the next runnable story (deterministic, lowest `id` first):
   ```cypher
   MATCH (c:Change {name: "<name>"})-[:HAS_STORY]->(s:Story)
   WHERE s.status = "pending"
     AND NOT EXISTS {
       MATCH (s)-[:DEPENDS_ON]->(d:Story)
       WHERE d.status <> "done"
     }
   RETURN s ORDER BY s.id LIMIT 1
   ```
2. **No runnable story:** run the blocked/complete check (Phase 4).
3. **Mark in progress:**
   ```cypher
   MATCH (s:Story {id: "<story-id>", change: "<name>"})
   SET s.status = "in_progress"
   ```
4. **Implement** the story. Read its full `description` from the graph; it is fully-specified, so implement without making assumptions. If the description is ambiguous, PAUSE and ask — do not guess. Keep changes minimal and scoped.
5. **Verify** each acceptance criterion against the implementation.
6. **Mark done** and synchronize:
   ```cypher
   MATCH (s:Story {id: "<story-id>", change: "<name>"})
   SET s.status = "done"
   ```
   Then sync the task checkboxes:
   ```bash
   python3 <repo>/scripts/story-driver.py sync-tasks "<name>" "<story-id>" --root <changeRoot>
   ```
   And append a compact summary:
   ```bash
   python3 <repo>/scripts/story-driver.py append-state "<name>" "<story-id>: <one-line summary of what changed and key decisions>" --root <changeRoot>
   ```
7. **Compact context.** You are about to move to a new story. Compress the current session context (drop implementation details that are recorded in `.story-state.md`). Before polling the next story, reload the state summary from `<changeRoot>/.story-state.md`.
8. **Confirm with the user** before starting the next story. Report what completed and what is next. If the user says continue, return to step 1; otherwise stop here.

### Phase 4 — Blocked and complete detection

When no story is runnable:

```cypher
MATCH (c:Change {name: "<name>"})-[:HAS_STORY]->(s:Story)
WITH count(s) AS total,
     count(CASE WHEN s.status = "done" THEN 1 END) AS done
RETURN total, done, total - done AS remaining
```

- **If `remaining = 0`:** all stories are complete. Report completion and suggest running the archive step (`/opsx-archive`).
- **If `remaining > 0`:** the dependency chain is blocked. List the blocked stories (pending stories with unmet dependencies) and STOP with a clear message.

## Graph Model Reference

```
(:Change {name: string})
(:Story {
  id: string,                  // kebab-case, unique within change
  title: string,
  description: string,         // fully-specified, zero-assumption
  acceptanceCriteria: [string],// max 3
  status: string,              // pending | in_progress | done
  taskRefs: [string]           // task descriptions this story covers
})
(:Change)-[:HAS_STORY]->(:Story)
(:Story)-[:DEPENDS_ON]->(:Story)  // A DEPENDS_ON B => A requires B first
```

Readiness rule: a story is runnable when `status = pending` and none of its `DEPENDS_ON` targets have a status other than `done`.

## Guardrails

- Verify MCP reachability before any action; never proceed silently into a broken state.
- Stories must have at most 3 acceptance criteria and zero-assumption descriptions; pause if a story is ambiguous rather than guessing.
- `taskRefs` across all stories must cover every `tasks.md` task exactly once.
- Never seed over an in-progress run; resume the existing graph state instead.
- Compact context after each story and reload `.story-state.md` before polling the next.
- Update task checkboxes and graph status promptly when a story completes.
- Stop and report when blocked; suggest archive when complete.
