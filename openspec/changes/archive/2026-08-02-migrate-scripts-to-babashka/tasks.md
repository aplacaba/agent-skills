## 1. Babashka setup and compatibility gate

- [x] 1.1 Install babashka (`curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && chmod +x install && ./install`) and confirm `bb --version` runs
- [x] 1.2 Verify the four bundled namespaces load in bb: `clj-yaml.core`, `cheshire`, `babashka.cli`, `babashka.fs`
- [x] 1.3 Gate: confirm `clj-yaml.core/parse-string` parses the fixture `stories.yaml` (must contain nested lists and at least one literal block scalar); if it fails, upgrade bb to latest stable and re-verify, or stop and re-open the D3 decision
- [x] 1.4 Record the tested bb version as the documented minimum in `docs/install.md`

## 2. Capture golden outputs from the current Python implementation

- [x] 2.1 Create fixture files under `scripts/test/fixtures/`: a sample `tasks.md` (multiple groups, mixed checkbox states) and a sample `stories.yaml` (stories with non-ASCII text, quotes, backslashes, and newline control characters in BOTH descriptions and acceptance criteria, at least one literal block scalar in a description, `dependsOn`, `taskRefs`)
- [x] 2.2 Run the current `story-driver.py generate` on the fixtures and save the outputs as golden `stories.md` and `story-seed.cypher` (normalized: header line names `story_driver.clj`)
- [x] 2.3 Save golden expectations for `parse-tasks` plain and `--json` output, and for `sync-tasks`/`append-state` behavior, captured from the current Python implementation

## 3. Port story_driver.clj

- [x] 3.1 Create `scripts/story_driver.clj` with `#!/usr/bin/env bb` shebang and root dispatch: no subcommand → `error: the following arguments are required: command` (exit 2), unknown subcommand → `invalid choice` (exit 2), `-h`/`--help` → help to stdout exit 0
- [x] 3.2 Implement per-subcommand flag parsing: `parse-tasks <tasks.md> [--json]`, `generate <change> [--root <path>] [--def <path>]`, `sync-tasks <change> <story-id> [--root <path>] [--def <path>]`, `append-state <change> <text> [--root <path>]` — reject unknown flags, missing/surplus positionals, and flags on wrong subcommands with usage to stderr and exit 2
- [x] 3.3 Implement `parse-tasks`: parse `##` groups and `- [x]`/`- [ ]` tasks from tasks.md, emit plain or `--json` output matching golden expectations
- [x] 3.4 Implement `stories.yaml` loading via `clj-yaml.core/parse-string` with file/format-level validation (exit 1, `error: ...` on stderr): missing definition file, non-mapping YAML, `change` mismatch, empty stories list
- [x] 3.5 Implement per-story validation (exit 1, `error: ...` on stderr): duplicate ids, non-kebab-case id, missing title/description, acceptance-criteria count outside 1-3
- [x] 3.6 Implement graph-level validation (exit 1, `error: ...` on stderr): unknown `dependsOn`, dependency cycle, taskRef not in tasks.md, tasks.md absent, uncovered tasks
- [x] 3.7 Implement `generate`: write `stories.md` (header names `story_driver.clj`) and `story-seed.cypher`; cypher string literals quoted with `cheshire.core/generate-string` (`:escape-non-ascii false`), arrays built by joining elements with `", "` inside `[ ]`; keep the post-loop orphan check for code parity (unreachable from CLI)
- [x] 3.8 Implement `sync-tasks`: toggle matching `[ ]` checkboxes referenced by the story's `taskRefs` to `[x]` in tasks.md, print `toggled N task(s) for story <id>` on success; unknown story id → `error:` exit 1
- [x] 3.9 Implement `append-state`: create `.story-state.md` with header on first call, append `- <text>` lines on subsequent calls, print `appended to <path>` on success

## 4. Test suite (clojure.test, no external deps)

- [x] 4.1 Create `scripts/test_story_driver.clj` using `clojure.test/deftest` with explicit `(clojure.test/run-tests)` that exits non-zero on test failures
- [x] 4.2 Add golden parity tests: `generate` output matches normalized goldens byte-for-byte (header line is the sole intentional difference)
- [x] 4.3 Add happy-path tests: `parse-tasks` plain + `--json`; `sync-tasks` toggling (only referenced tasks) with resulting `tasks.md` bytes AND success stdout (`toggled N task(s) for story <id>`) compared against expectations; `append-state` first-call header + append with resulting `.story-state.md` bytes AND success stdout compared against expectations
- [x] 4.4 Add file/format-level validation error tests (exit 1 + stderr text): missing definition file, non-mapping YAML, change mismatch, empty stories list
- [x] 4.5 Add per-story validation error tests (exit 1 + stderr text): duplicate ids, non-kebab-case id, missing title/description, AC count outside 1-3
- [x] 4.6 Add graph-level validation error tests (exit 1 + stderr text): unknown dependsOn, cycle, taskRef not in tasks.md, tasks.md absent, uncovered tasks, unknown story id in `sync-tasks`
- [x] 4.7 Add parser-rejection tests asserting exit code 2 + usage for: no subcommand (`the following arguments are required: command`), unknown subcommand (`invalid choice`), unknown flags, missing positionals, surplus positionals, flags on the wrong subcommand
- [x] 4.8 Add help tests: `-h`/`--help` at root and on each subcommand prints help to stdout and exits 0
- [x] 4.9 Create `scripts/test_config_merge.clj` (clojure.test, run-tests with non-zero exit on failure) covering JSONC parsing: comments, trailing commas, string contents containing `//` and `/*`, malformed config (`could not parse <path>:` prefix on **stderr** + non-zero exit)
- [x] 4.10 Add config-merge behavior tests: new config creation, unrelated keys preserved, `opencode.json` over `opencode.jsonc`, existing neo4j block preserved (no backup), merge-with-edit writes backup

## 5. Port setup.sh Python to babashka

- [x] 5.1 Add `check_cmd bb "install babashka (https://babashka.org)"` to the hard prerequisites; remove the `python3` and PyYAML checks
- [x] 5.2 Replace the `python3` realpath line: resolve `REPO_ROOT` AFTER the hard-prereq gate using `bb -e '(require (quote [babashka.fs :as fs])) (println (fs/canonicalize (first *command-line-args*)))' "$(dirname "${BASH_SOURCE[0]}")"`
- [x] 5.3 Create `scripts/config-merge.clj`: read `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`, `OPENCODE_CONFIG_DIR` from env; implement JSONC comment/trailing-comma stripping; implement `opencode.json`/`.jsonc` discovery
- [x] 5.4 Implement merge semantics in `scripts/config-merge.clj`: backup-on-edit, idempotent `mcp.neo4j` merge, preserve unrelated keys, skip when no password
- [x] 5.5 Replace the `python3 <<'PYEOF'` heredoc in setup.sh with `bb "$REPO_ROOT/scripts/config-merge.clj"`
- [x] 5.6 Run `./setup.sh` end-to-end twice against a scratch `OPENCODE_CONFIG_DIR` (with and without `NEO4J_PASSWORD`) and confirm idempotency, backups, and unchanged behavior
- [x] 5.7 Behavioral check: run `./setup.sh` with `bb` absent from PATH — must print the install hint and exit non-zero; run it on a machine without `python3`/PyYAML — must neither check nor report them

## 6. Update references

- [x] 6.1 Update `skills/openspec-story-driver/SKILL.md`: replace all `python3 <repo>/scripts/story-driver.py <command> ...` invocations with `bb <repo>/scripts/story_driver.clj <command> ...`
- [x] 6.2 Update `commands/opsx-story.md`: `story-driver.py generate` → `bb <repo>/scripts/story_driver.clj generate`
- [x] 6.3 Update `docs/install.md`: babashka required prerequisite with install command, tested minimum version, remove Python 3 + PyYAML
- [x] 6.4 Update `docs/harness-mapping.md`: story-driver entry references `bb <repo>/scripts/story_driver.clj`, babashka runtime requirement, no `story-driver.py`/Python/PyYAML mentions

## 7. Remove Python artifacts

- [x] 7.1 Delete `scripts/story-driver.py` and `scripts/__pycache__/`
- [x] 7.2 Update `.gitignore`: remove `__pycache__/` and `*.pyc` entries
- [x] 7.3 Grep the repo for stale references (`story-driver.py`, `python3`, `PyYAML`) and confirm none remain outside `openspec/` history

## 8. Full verification pass

- [x] 8.1 Run the full bb test suite (`bb scripts/test_story_driver.clj` and `bb scripts/test_config_merge.clj`) and confirm all tests pass with non-zero-exit enforcement
- [x] 8.2 Run `./setup.sh` twice on a fresh scratch `OPENCODE_CONFIG_DIR` and verify symlinks, plugin, merged config, and idempotency of the second run
- [x] 8.3 Confirm delta specs match the implemented behavior (byte-identical artifacts, CLI contract, bb prerequisite, updated docs)
