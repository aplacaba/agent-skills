## 1. Skill and command scaffolding

- [x] 1.1 Create `.opencode/skills/openspec-story-driver/SKILL.md` with frontmatter (name, description, allowed-tools, license, metadata) and the story-driven apply workflow
- [x] 1.2 Create `.opencode/commands/opsx-story.md` as the entry point that loads the skill

## 2. Decomposition capability

- [x] 2.1 Define the zero-assumption story template (title, exact file paths, exact behavior, edge cases, max 3 acceptance criteria, dependencies, taskRefs)
- [x] 2.2 Implement story decomposition from `tasks.md` using proposal/design/specs as context, splitting stories that exceed 3 acceptance criteria
- [x] 2.3 Write human-readable `stories.md` into the change root listing every story and its dependencies
- [x] 2.4 Write idempotent `story-seed.cypher` (MERGE-based) into the change root for the decomposed stories

## 3. Story graph capability

- [x] 3.1 Document and implement the Cypher graph model: `Change`, `Story` (id, title, description, acceptanceCriteria, status, taskRefs), `HAS_STORY`, `DEPENDS_ON`
- [x] 3.2 Implement the readiness query returning the next runnable story (pending, all DEPENDS_ON targets done, deterministic order)
- [x] 3.3 Implement seed logic that skips re-seeding when a run is in progress for that change
- [x] 3.4 Implement status transitions pending → in_progress → done

## 4. Execution loop capability

- [x] 4.1 Implement MCP availability check with setup guidance (Docker command + opencode MCP config) on failure
- [x] 4.2 Implement the poll → implement → mark done loop, including deterministic next-story selection
- [x] 4.3 Implement task checkbox synchronization from story `taskRefs` into `tasks.md`
- [x] 4.4 Implement `.story-state.md` summary appending and context compaction between stories
- [x] 4.5 Implement blocked detection (report remaining blocked stories and stop) and completion detection (suggest archive)
- [x] 4.6 Implement step-with-confirmation behavior after each completed story

## 5. Verification

- [x] 5.1 Test decomposition on a sample change producing valid `stories.md` and `story-seed.cypher`
- [x] 5.2 Test seeding idempotency and readiness query with dependency chains in Neo4j
- [x] 5.3 Test the full loop end-to-end: complete, blocked, and in-progress states
