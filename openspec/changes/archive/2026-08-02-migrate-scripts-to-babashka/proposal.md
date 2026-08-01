## Why

The repo's helper scripting depends on Python: `scripts/story-driver.py` (the story-driver CLI) and inline Python embedded in `setup.sh` (repo-root resolution, JSONC-stripping, and the Neo4j MCP config merge). This adds a second runtime to the install surface (Python 3 + PyYAML) for what are small, mostly text-processing tasks. Babashka (`bb`) gives a single fast-starting runtime that covers both the CLI and the config-merge logic, so one tool replaces Python for all repo scripting.

## What Changes

- **BREAKING**: Replace `scripts/story-driver.py` with `scripts/story_driver.clj` (babashka). The Python file and its `__pycache__` are removed; existing direct consumers must invoke the new script via `bb`. The CLI contract is preserved: same subcommands (`parse-tasks`, `generate`, `sync-tasks`, `append-state`), same flags (`--json`, `--root`, `--def`), same exit-on-error behavior.
- Replace the inline Python in `setup.sh` with babashka: repo-root resolution, JSONC comment/trailing-comma stripping, and the idempotent Neo4j MCP config merge.
- **BREAKING**: Make `bb` a hard prerequisite in `setup.sh`; remove the `python3` and PyYAML prerequisite checks. Python 3 + PyYAML are no longer required.
- Update all references to the old script and Python requirement: `skills/openspec-story-driver/SKILL.md`, `commands/opsx-story.md`, `docs/harness-mapping.md`, `docs/install.md`, and the affected specs.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `install-setup`: prerequisite validation changes from `python3` + PyYAML to `bb` (hard); setup no longer shells out to Python for the config merge.
- `harness-adapters`: canonical `scripts/` content changes from `story-driver.py` (Python 3) to `story_driver.clj` invoked via `bb`; the adapter/skill references must point at the new script and invocation.

## Impact

- `scripts/story-driver.py` → `scripts/story_driver.clj` (bb), Python file removed.
- `setup.sh`: prerequisite checks, repo-root resolution, JSONC merge rewritten in Clojure via `bb`.
- `.gitignore`: remove Python cache entries (`__pycache__`/`*.pyc`).
- References updated in: `skills/openspec-story-driver/SKILL.md`, `commands/opsx-story.md`, `docs/harness-mapping.md`, `docs/install.md`, `openspec/specs/install-setup/spec.md`, `openspec/specs/harness-adapters/spec.md`.
- New dependency: babashka (`bb`) required at install and runtime. Python/PyYAML no longer required.
- No behavior change to the story-driven workflow: the same files (`stories.md`, `story-seed.cypher`, `tasks.md` toggling, `.story-state.md`) are produced by the same command names.
