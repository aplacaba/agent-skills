## Why

The Neo4j story graph currently models `Change` → `HAS_STORY` → `Story` in isolation: there is no notion of which project a change belongs to, and no way to classify projects (tech stack, type, repo location) for retrieval or cross-project queries. Every graph starts empty and nothing links changes back to their owning repository, so multi-project setups cannot answer "what projects exist" or "which changes belong to project X".

## What Changes

- Extend the story graph model with a `Project` node holding classification metadata: `name` (stable identity key), `repoUrl`, `techStack` (list), and `type` (e.g. `tooling`, `agent`, `docs`). The project `name` is derived canonically from the repository: the git remote `origin` URL normalized to `host/owner/repo` (lowercase, e.g. `github.com/aplacaba/agent-skills`), which distinguishes repositories across hosts and owners. If `origin` is absent or unusable, the fallback is `local/<repo-directory-name>` (e.g. `local/my-agent-skill`). The same repository therefore always yields the same project name while its `origin` (or lack of one) is unchanged; callers pass that name as `--project`. Repositories without a usable `origin` are inherently unverifiable, so `local/<dir>` names are best-effort identity: two unrelated local repositories with the same directory name are documented as sharing a project until an `origin` is added, and agents are instructed to prefer setting `origin` over relying on the fallback. Adding/removing an `origin` later changes the derived project name, which intentionally starts a new project identity with no automatic migration of graph state.
- Add an ownership edge `Project` → `BELONGS_TO` → `Change` so every seeded change is linked to its project.
- **BREAKING**: `Change` identity becomes project-scoped: `MERGE (c:Change {name: <name>, project: <project>})`. Two repositories with the same change name no longer share a `Change` node. `Story` identity and dependency matching become project-scoped too: stories merge as `{id, change, project}`, `HAS_STORY` and `DEPENDS_ON` edges match on all three properties, so same-named changes/stories in different projects share no execution state.
- **BREAKING**: all graph queries on the story graph (next-runnable story, status transitions, readiness, story selection) filter by `project`, including direct `Story`-node updates in the lifecycle flow — not just queries that start from a `Change` node.
- **BREAKING**: `generate` gains a required `--project <name>` flag (derived from the repo by the caller) and emits the `MERGE (p:Project {name: <project>})` node plus `MERGE (p)-[:BELONGS_TO]->(c)` edge in `story-seed.cypher`. The seed always links the change to its project.
- The story-graph capability gains a requirement to register/classify a project: an agent derives the classification from repository facts (README, package/manifest files, directory layout) and writes the `Project` node's classification properties via the Neo4j MCP server's Cypher execution tool. Re-running classification refreshes the metadata (`SET` semantics on `MERGE` by `name`).
- The next-runnable-story query and story lifecycle behave as before except that all story-graph queries are project-scoped (polling, readiness, status transitions, blocked/completion checks, story selection, and the seeding/resume guard).
- **BREAKING**: `story-seed.cypher` output changes (project-scoped `Change` and `Story` identity, `Project` node, `BELONGS_TO` edge), so the harness-adapters byte-identical-artifact and preserved-CLI-contract requirements are updated accordingly.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `story-graph`: the data model gains `Project` nodes (`name`, `repoUrl`, `techStack`, `type`), `BELONGS_TO` edges from `Project` to `Change`, project-scoped `Change` and `Story` identity (including the readiness query and the seeding/resume guard), a project registration/classification flow, and idempotent project linking during seeding.
- `story-execution`: the polling, status-transition, blocked-detection, and completion queries gain the project filter (project-scoped story selection and updates).
- `harness-adapters`: the byte-identical `story-seed.cypher` requirement and the preserved story-driver CLI contract (new required `--project` flag) are updated.

## Impact

- `openspec/specs/story-graph/spec.md`, `openspec/specs/story-execution/spec.md`, and `openspec/specs/harness-adapters/spec.md` — requirement-level changes (delta specs).
- `scripts/story_driver.clj` — `generate` requires `--project <name>` and emits the project-scoped `Change` and `Story` nodes, `Project` node, and `BELONGS_TO`/`HAS_STORY`/`DEPENDS_ON` edges in the seed; `parse-tasks`/`sync-tasks`/`append-state` unchanged.
- `scripts/test_story_driver.clj` — golden `story-seed.cypher` fixtures updated for the new node/edge/identity; new tests for `--project` (required, missing value, seeding shape).
- `skills/openspec-story-driver/SKILL.md` — documents the classification flow (deriving facts from the repo, writing via the Cypher tool), the `--project` flag, and project-scoped queries.
- `commands/opsx-story.md` — workflow summary updated to pass `--project` and mention classification.
- `AGENTS.md` — the story-driver invocation list gains the `--project` flag (fact-update rule applies).
- `docs/harness-mapping.md` — unchanged (Cypher tool already mapped).
- No changes to story decomposition or install; harness adapters change only for the parity/CLI-contract updates.
- No new dependencies: the Cypher execution tool of the Neo4j MCP server is the only write path, consistent with the existing spec.
- **BREAKING**: Pre-existing graph data: `Change` and `Story` nodes already in the graph without a `project` property are not automatically migrated; they are treated as legacy/unowned and excluded from project-scoped queries (previously unowned runs disappear from polling/resume after deployment). The registration flow classifies `Project` nodes only; it does not link legacy changes. Automatic migration is out of scope.
