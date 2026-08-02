## 1. story_driver.clj: --project flag and project-scoped seed

- [x] 1.1 Add `--project <name>` to the `generate` subcommand spec in `parse-args`: required flag, allowed on generate only; missing flag → usage + `the following arguments are required: --project` on stderr, exit 2; missing value or flag-as-value → `argument --project: expected one argument`, exit 2
- [x] 1.2 Thread `project` through `cmd-generate` into `write-seed`; update the seed header comment to include the project line
- [x] 1.3 Update `write-seed` to emit project-scoped statements: `MERGE (p:Project {name: <project>});`, `MERGE (c:Change {name: <change>, project: <project>});`, literal `MATCH (p:Project ...), (c:Change ...) MERGE (p)-[:BELONGS_TO]->(c);`, `Story` nodes as `{id, change, project}` with `ON CREATE SET` unchanged, `HAS_STORY` and `DEPENDS_ON` MATCHes with three-property filters
- [x] 1.4 Update `story_driver.clj` usage/help text (`generate` usage line gains required `--project PROJECT`; root help mentions the flag as required)

## 2. Fixtures and tests

- [x] 2.1 Regenerate `scripts/test/fixtures/golden/story-seed.cypher` from the new `generate --project fixture-project` output (and `stories.md` if unchanged confirm it); keep the fixture change-root stories.yaml/tasks.md as-is
- [x] 2.2 Update `scripts/test_story_driver.clj` generate-parity tests to invoke `--project` and compare against the updated goldens
- [x] 2.3 Add parser tests: `generate` without `--project` exits 2 with the required-flag message; `--project` with no value exits 2 with expected-one-argument; `--project --root <path>` (flag as value) exits 2 with expected-one-argument; `--project` on parse-tasks/sync-tasks/append-state is rejected (exit 2)
- [x] 2.4 Add a seed-shape test asserting the emitted cypher contains `Project {name}`, `Change {name, project}`, `Story {id, change, project}`, `BELONGS_TO`, and three-property `HAS_STORY`/`DEPENDS_ON` statements

## 3. Skill, command, and AGENTS.md updates

- [x] 3.1 Update `skills/openspec-story-driver/SKILL.md`: `generate` invocations gain `--project <name>`; add a "Classify the project" step documenting D1 name derivation (git origin → host/owner/repo, subgroup preservation, local fallback, canonical across clones), D4 facts (type vocabulary, techStack normalization, repoUrl sanitization) and the classification Cypher with full-overwrite semantics (`MERGE (p:Project {name}) SET p.repoUrl = ..., p.techStack = [...], p.type = ...;` where values no longer derivable are written as `null` to clear them)
- [x] 3.2 Update every Cypher snippet in the SKILL.md to be project-scoped: seeding/resume guard, next-runnable poll, status transitions (`{id, change, project}`), blocked/completion checks, and the graph-schema reference section
- [x] 3.3 Update `commands/opsx-story.md`: the generate step derives the canonical project name from the repository's git `origin` per the story-graph classification contract (accepted URL forms, normalization, subgroup preservation, `local/<dir>` fallback) and passes it as `--project`; workflow summary mentions project classification
- [x] 3.4 Update `AGENTS.md` story-driver invocation list: `generate <change> --project <name> [--root <changeRoot>] [--def <stories.yaml>]`

## 4. Verification

- [x] 4.1 Run `bb scripts/test_story_driver.clj` and `bb scripts/test_config_merge.clj` — all assertions pass
- [x] 4.2 Manual check: run `generate` with `--project`, inspect `story-seed.cypher` for the project-scoped statements; run `generate` without `--project` and confirm exit 2 + usage on stderr; confirm `generate -h` shows `--project` as required
- [x] 4.3 Documentation verification: grep `skills/openspec-story-driver/SKILL.md` and `commands/opsx-story.md` — every `generate` invocation passes `--project`; the classification step documents D1 derivation (incl. local fallback), D4 full-overwrite/null-clearing, and repoUrl sanitization; every Cypher snippet in the skill matches `Change`/`Story` with a `project` property
- [x] 4.4 If a live graph is available, run the seed twice and confirm idempotency (no duplicate Project/Change/Story nodes or edges)
