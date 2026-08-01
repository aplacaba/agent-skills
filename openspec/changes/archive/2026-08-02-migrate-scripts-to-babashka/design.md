## Context

The repo's scripting currently requires two runtimes:

- `scripts/story-driver.py` — a 283-line Python CLI (parse-tasks, generate, sync-tasks, append-state) used by the `openspec-story-driver` skill and `/opsx-story`. It needs Python 3 + PyYAML.
- `setup.sh` — a bash installer that shells out to inline Python for three things: repo-root realpath resolution, JSONC comment/trailing-comma stripping, and the idempotent Neo4j MCP config merge.

The proposal (`proposal.md`, frozen) commits to: rewrite both to babashka, make `bb` a hard prerequisite, drop the Python/PyYAML checks, remove `scripts/story-driver.py` and `__pycache__` artifacts, and update all references (skill, command, docs, specs). Behavior contracts of the story-driven workflow are preserved: same subcommands, same flags, same exit-on-error behavior, same output files (`stories.md`, `story-seed.cypher`, toggled `tasks.md`, `.story-state.md`).

Babashka is not yet installed on the authoring machine, so the first implementation task must install it (`curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && chmod +x install && ./install`) and confirm `bb --version` works before porting.

## Goals / Non-Goals

**Goals:**
- Port `scripts/story-driver.py` → `scripts/story_driver.clj` (Clojure, babashka), CLI-contract identical: subcommand names, positional args, `--json`, `--root`, `--def` flags, `error: ...` messages on stderr + non-zero exit.
- Port the three Python usages in `setup.sh` to babashka: repo-root canonicalization, JSONC strip + trailing-comma removal, and the Neo4j MCP merge (backup, idempotency, preserve-existing-block behavior).
- Make `bb` a hard prerequisite in `setup.sh`; remove `python3`/PyYAML checks.
- Byte-parity for generated artifacts where feasible (`stories.md` layout, cypher seed content, JSON string escaping).
- Update every reference: `skills/openspec-story-driver/SKILL.md`, `commands/opsx-story.md`, `docs/install.md`, `docs/harness-mapping.md`, and the two delta specs.
- Provide automated tests that prove parity and run with just `bb` (no extra deps).

**Non-Goals:**
- No new subcommands, flags, or output formats — behavior parity only.
- No rewrite of the bash orchestration itself (symlinking, loops, prompts stay in bash; only Python usages move to bb).
- No changes to the Neo4j MCP merge semantics beyond the language port.
- No requirement-level changes to story-decomposition, story-execution, or story-graph — their specs are untouched.

## Decisions

### D1: One bb script per concern, invoked as `bb <repo>/scripts/<name>.clj`

- `scripts/story_driver.clj` — the four subcommand CLI.
- `scripts/config-merge.clj` — the JSONC strip + trailing-comma removal + neo4j MCP merge, reading `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`, `OPENCODE_CONFIG_DIR` from the environment, invoked by `setup.sh`.

*Alternatives considered:* a single mega-script (rejected: two unrelated concerns, worse testability); inline `bb -e`/heredoc in `setup.sh` (rejected: quoting hell for multi-line Clojure, untestable in isolation). Rationale: separate files match the existing `scripts/` convention, are directly testable with `bb`, and keep `setup.sh` diff small.

### D2: Subcommand dispatch = manual `case` on the first arg + strict flag parsing

`(case (first args) "parse-tasks" ... )` dispatches, then `babashka.cli/parse-args` on the remainder handles `--json` (boolean), `--root <path>`, `--def <path>`. `--def` maps to the `:def` keyword. Because `babashka.cli/parse-args` is open-world by default, the dispatch layer SHALL explicitly reproduce argparse's behavior at the top level and per subcommand, distinguishing three behaviors:

- **Root-level parser failures**: no subcommand → argparse-style usage plus `error: the following arguments are required: command` on stderr; unknown subcommand → usage plus `error: invalid choice: '...'` on stderr. Both exit code **2** (argparse exits 2 for parser errors).
- **Subcommand parser failures**: unknown options, missing required positionals, surplus positionals → argparse-style usage on stderr, exit code **2**.
- **Application validation failures** (bad input data): print `error: ...` to stderr and exit with code **1**, matching `sys.exit("error: ...")` in the current script.

**Help**: `-h` / `--help` at root and per-subcommand SHALL print the corresponding help text to stdout and exit **0**, as argparse does.

*Alternatives considered:* full `babashka.cli` subcommand config (rejected: `:sub-command` parsing adds indirection for only four commands and makes positional error messages harder to control); hand-rolled flag parser (rejected: unnecessary code when `babashka.cli` ships with bb).

### D3: YAML via bundled clj-yaml

Babashka bundles `clj-yaml`. An early verification task confirms `(require '[clj-yaml.core :as yaml])` loads and `yaml/parse-string` (fully qualified: `clj-yaml.core/parse-string`) parses the fixture `stories.yaml` (which contains nested lists and literal block scalars, not a flat subset). If the installed bb build lacks it, upgrade to the latest stable bb; if no stable bb bundles clj-yaml, stop and re-open D3 with the user (the format stays YAML; a subset reader would silently accept a narrower input than the preserved CLI contract).

*Alternatives considered:* adding a deps.edn/classpath dependency (rejected: defeats single-binary zero-deps goal of bb); switching stories.yaml to EDN (rejected: user-facing format change, out of scope).

### D4: JSON via bundled cheshire, byte-parity by construction

Python embeds only arrays of strings in `story-seed.cypher` (`acceptanceCriteria`, `taskRefs`), serialized with `json.dumps(..., ensure_ascii=False)`, whose only separator is `, ` between elements. To avoid relying on cheshire's configurable output (which does not support Python-style separators), the port SHALL build these literals by hand: quote each element with `cheshire.core/generate-string` (`:escape-non-ascii false`) and join with `", "` inside `[ ]`. This makes `["a", "b"]` byte-identical to the Python baseline by construction; a golden test still asserts the exact generated lines.

*Alternatives considered:* `:pretty` cheshire output (rejected: Jackson pretty-printing differs from Python's spacing, e.g. `"a" : 1`); post-processing cheshire's compact output (rejected: string-surgery on serialized output is fragile); `babashka.json` (same compact-output problem).

### D5: Parity tests via `clojure.test` + golden outputs, no external deps

A test file `scripts/test_story_driver.clj` using `clojure.test/deftest` (bundled in bb), ending with an explicit `(clojure.test/run-tests)` that maps test failures to a non-zero process exit (tests do not run by declaration alone). Command: `bb scripts/test_story_driver.clj`. Golden fixtures: a sample `tasks.md`, a sample `stories.yaml`, and the expected `stories.md` + `story-seed.cypher` captured from the current Python output during migration. The sole intentional difference from the captured Python goldens is the generated-file header line in `stories.md`, which must name `story_driver.clj` instead of `story-driver.py` (a proposal-mandated reference update); the goldens are normalized for that line. Tests cover, per preserved CLI contract:

- `generate` happy path: generated `stories.md` and `story-seed.cypher` match goldens byte-for-byte (after header normalization).
- `parse-tasks` happy paths: plain output and `--json` output match expected structure (groups, task numbering, checkboxes).
- `sync-tasks` happy path: correct checkboxes toggled from `[ ]` to `[x]`, others untouched; output message.
- `append-state` happy paths: file created on first call (with header), appended on subsequent calls, output message.
- Error paths, asserting both exit code and stderr message text, mirroring the current script's validation set: story definition file absent; definition not a YAML mapping; `change` mismatch between definition and argument; empty stories list; duplicate story ids; non-kebab-case story id; missing title/description; acceptance-criteria count outside 1-3; unknown `dependsOn` reference; dependency cycle; taskRef not found in tasks.md (exits at the first unknown reference); tasks.md absent; tasks not covered by any story; unknown story id during `sync-tasks`. Note: the Python script's post-loop `taskRefs not in tasks.md` orphan check is unreachable from the CLI (the loop exits at the first unknown reference); the port keeps the check for code parity but no CLI test asserts it, since no input can reach it.
- Parser rejection parity: root level (no subcommand → `the following arguments are required: command`; unknown subcommand → `invalid choice`), per-subcommand (unknown flags, missing required positionals, surplus positionals) — asserting argparse's exit code 2 and usage-on-stderr, distinct from validation failures (exit 1). Help: `-h`/`--help` at root and per subcommand prints help and exits 0.
- Escaping parity: the fixture `stories.yaml` includes non-ASCII text, quotes, backslashes, and newline control characters in descriptions/acceptance criteria so string escaping is exercised in the cypher literals.

`scripts/config-merge.clj` gets a test with JSONC fixtures: comments, trailing commas, string contents containing `//` and `/*`, malformed config → `could not parse <path>:` on stderr and non-zero exit (matching the current `sys.exit(f"could not parse {path}: {exc}")`, which prints no `error:` prefix; the parser-generated suffix after the colon is not byte-matched, only the prefix and exit code), no config file → new config created with merged `mcp.neo4j`, unrelated keys preserved across a merge, `opencode.json` preferred over `opencode.jsonc`, existing neo4j block preserved (no backup written, no edit), and a merge that does edit (backup written) — backup expectation is tied to an actual edit, matching current behavior.

*Alternatives considered:* shell-based smoke tests only (rejected: weak coverage of the validation logic); adding a full Clojure test framework via deps (rejected: bb should stay dependency-free).

### D6: `setup.sh` — keep bash, replace only the Python bits, resolve REPO_ROOT after the bb gate

- Prereq check: `check_cmd bb "install babashka (https://babashka.org)" || missing_hard=1`; delete the `python3` and PyYAML checks.
- Repo root: resolved AFTER the hard-prereq gate has passed (a missing `bb` under `set -euo pipefail` would abort before the install hint is printed otherwise): `REPO_ROOT="$(bb -e '(require (quote [babashka.fs :as fs])) (println (fs/canonicalize (first *command-line-args*)))' "$(dirname "${BASH_SOURCE[0]}")")"`. The current Python realpath line (executed before the prereq section) moves to after the gate.
- Config merge: replace the `python3 <<'PYEOF'` heredoc with `bb "$REPO_ROOT/scripts/config-merge.clj"` (env vars already exported in scope).

*Alternatives considered:* porting the whole installer to bb (rejected: bash is the right tool for symlink orchestration; bb's process-spawning ergonomics for `ln` loops are worse; larger diff, more risk for zero benefit).

### D7: Shebang and invocation convention

`story_driver.clj` starts with `#!/usr/bin/env bb` so `./scripts/story_driver.clj` also works, but all docs/skill references use the explicit `bb <repo>/scripts/story_driver.clj <command> ...` form (consistent with how `openspec` CLI is invoked and unambiguous when the script is not executable).

## Risks / Trade-offs

- [bb not installed on target machines or authoring machine] → `setup.sh` hard-prereq check with install hint; first implementation task installs bb and verifies `bb --version`; `docs/install.md` lists babashka first.
- [Bundled `clj-yaml` unavailable or version drift across bb releases] → D3 verification task runs first; on missing bundling, upgrade to latest stable bb and document the minimum version in `docs/install.md`; if no stable bb bundles it, stop and re-open D3 with the user rather than narrowing the accepted YAML format.
- [JSON/string escaping or file formatting drift vs Python baseline] → golden-output parity tests (D5) compare byte-for-byte against outputs captured from the current implementation; D4 eliminates separator drift by construction.
- [Behavioral regression in error paths (exit codes, stderr messages)] → parity tests assert exit code and `error:` message text for each validation failure.
- [setup.sh merge logic regression (JSONC edge cases)] → `config-merge.clj` unit tests cover comments, trailing commas, string contents containing `//`, existing-block preservation, backup.
- [Breaking change for any consumer invoking `story-driver.py` directly] → all in-repo references updated in the same change; external consumers are told via the **BREAKING** marker in the proposal; the old Python file is deleted, so stale references fail loudly at first call.

## Migration Plan

1. Install babashka; confirm `bb --version`; confirm `clj-yaml.core`, `cheshire`, `babashka.cli`, and `babashka.fs` all load in bb (D3 gate). Record the tested bb version as the documented minimum in `docs/install.md`.
2. Capture golden outputs: run the current `story-driver.py` against a fixture change (sample `tasks.md` + `stories.yaml`) and save `stories.md` / `story-seed.cypher` as test fixtures.
3. Port `story_driver.clj`; make the parity tests pass under `bb` while `story-driver.py` still exists (side-by-side mode).
4. Port `config-merge.clj`; test JSONC merge against a scratch `OPENCODE_CONFIG_DIR` (edit → backup; existing block → no edit, no backup).
5. Update `setup.sh` (bb prereq, repo-root after gate, merge invocation); run `./setup.sh` against a scratch config dir end-to-end.
6. Update references: SKILL.md, opsx-story.md, docs/install.md, docs/harness-mapping.md.
7. Delete `scripts/story-driver.py`, `scripts/__pycache__/`; update `.gitignore` (drop `__pycache__/`, `*.pyc`).
8. Update delta specs (`install-setup`, `harness-adapters`) per this design.
9. Full pass: run all bb tests; re-run `./setup.sh` twice to confirm idempotency.

## Open Questions

- Minimum supported babashka version: resolved during step 1 (D3 gate) by testing that the four bundled namespaces (`clj-yaml.core`, `cheshire`, `babashka.cli`, `babashka.fs`) load. Operationally: the version tested and verified on this machine is recorded in `docs/install.md` as the minimum (not "the lowest possible" — the tested minimum, with the documented install command). If no stable bb bundles clj-yaml, D3 is re-opened with the user.
