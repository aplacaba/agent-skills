## 1. Extract facts from canonical content

- [x] 1.1 Read `setup.sh`, `docs/install.md`, and `docs/harness-mapping.md` and extract the exact prerequisite list (babashka, openspec CLI, docker, git; a running Neo4j instance is an operational requirement noted in docs, not a setup prerequisite), quick start command, and MCP server facts (mcp/neo4j-cypher via docker, NEO4J_* env vars)
- [x] 1.2 Read `skills/openspec-story-driver/SKILL.md`, `commands/*.md`, and `scripts/*.clj` and extract exact script/test invocations (`bb <repo>/scripts/story_driver.clj <cmd> ...`, `bb scripts/test_story_driver.clj`, `bb scripts/test_config_merge.clj`), workflow steps, and canonical directory layout
- [x] 1.3 Check `git log --oneline` for the commit subject pattern and confirm the conventional-style examples cited in design D2 appear in history

## 2. Write README.md

- [x] 2.1 Write `README.md` with: project purpose naming the opencode, Claude Code, and Codex harnesses; prerequisites; `./setup.sh` quick start; story-driven workflow summary; canonical directory layout table; links to `docs/install.md` and `docs/harness-mapping.md` — using only the facts extracted in group 1
- [x] 2.2 Verify every fact in `README.md` (prerequisites, script paths, directory layout, workflow and tool wording) against `setup.sh`, skills, commands, scripts, and docs; fix any mismatch

## 3. Write AGENTS.md

- [x] 3.1 Write `AGENTS.md` stating: OpenSpec propose→apply→verify→archive workflow with no code changes without a proposal; conventional commit format `<type>(<scope>): <summary>` with types feat/fix/docs/chore/refactor/test/perf/build, optional scope, imperative lowercase <80-char summary, body explaining why, and the two examples from design D2; harness-neutral wording rule per `docs/harness-mapping.md`; canonical directories; exact script/test invocations; mcp/neo4j-cypher MCP note
- [x] 3.2 Include the derived-fact rule in `AGENTS.md`: a change altering a derived documentation fact SHALL update the corresponding line in `README.md`/`AGENTS.md` in the same change and verify it against the repository
- [x] 3.3 Verify the invocations, MCP note, and commit examples in `AGENTS.md` match the current repo state and history; also confirm the no-proposal rule matches the observed repository pattern (prior work went through openspec changes), and the canonical directory names and harness-neutral wording match `docs/harness-mapping.md`; fix any mismatch

## 4. Final verification

- [x] 4.1 Confirm the product diff consists exclusively of adding `README.md` and `AGENTS.md`: `git status` may also show the openspec change artifacts (proposal/design/specs/tasks) and task-checkbox updates, which are expected — the check is that no other product file (skills/, commands/, agents/, scripts/, docs/, setup.sh, specs) appears modified
- [x] 4.2 Run `bb scripts/test_story_driver.clj` and `bb scripts/test_config_merge.clj` to confirm no regression (docs-only change, tests still green)
