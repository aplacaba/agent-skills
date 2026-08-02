## MODIFIED Requirements

### Requirement: Existing opencode workflow preserved

The move to the canonical layout and the babashka migration SHALL NOT break the existing opencode workflow: `/opsx-*` commands and the `openspec-story-driver` skill MUST still work in opencode after the change.

The preserved story-driver CLI contract SHALL be: subcommands `parse-tasks`, `generate`, `sync-tasks`, and `append-state`; required positional arguments per subcommand (`parse-tasks <tasks.md>`, `generate <change>`, `sync-tasks <change> <story-id>`, `append-state <change> <text>`); flag applicability limited to `--json` (parse-tasks only), `--root` (generate, sync-tasks, append-state), `--def` (generate, sync-tasks), and the new required `--project` (generate only); `-h`/`--help` at root and per subcommand printing help and exiting 0; exit-on-error behavior (parser errors exit 2 with usage on stderr; application validation errors exit 1 with `error: ...` on stderr).

The generated artifacts SHALL be byte-identical to the updated golden baseline: `stories.md` (with the header naming `story_driver.clj` as the sole intentional difference), `story-seed.cypher` (updated baseline including the project-scoped `Project`/`Change`/`Story` nodes and `BELONGS_TO` edge), `tasks.md` checkbox toggles, and `.story-state.md` appends.

#### Scenario: Commands still work

- **WHEN** the change is applied
- **THEN** `/opsx-apply`, `/opsx-propose`, `/opsx-archive`, `/opsx-sync`, `/opsx-explore`, and `/opsx-story` are discoverable in opencode

#### Scenario: Story-driver script runs via babashka

- **WHEN** the story-driver script is invoked as `bb <repo>/scripts/story_driver.clj <command> ...`
- **THEN** the `openspec-story-driver` skill references the babashka script path and invocation, and the script runs, producing the same artifacts as before with project-scoped seeds

#### Scenario: Canonical scripts directory holds the babashka script

- **WHEN** the repo is inspected after the change
- **THEN** `scripts/` contains `story_driver.clj` and `config-merge.clj`, and no Python script files remain

#### Scenario: opsx-story uses the babashka invocation

- **WHEN** the `/opsx-story` command is invoked in opencode
- **THEN** it runs the story-driver through `bb <repo>/scripts/story_driver.clj <command> ...`, derives the canonical project name from the repository's git `origin` (per the story-graph classification requirement) and passes it as `--project`, and completes its workflow without referencing `story-driver.py`

#### Scenario: CLI contract preserved with --project

- **WHEN** `generate` is run via `bb <repo>/scripts/story_driver.clj` without `--project`
- **THEN** it exits 2 with usage on stderr (parser error), and with `--project` it produces the project-scoped seed artifacts

#### Scenario: Harness-mapping documentation updated

- **WHEN** the repo is inspected after the change
- **THEN** `docs/harness-mapping.md` references the story-driver via `bb <repo>/scripts/story_driver.clj`, notes babashka as the runtime requirement, and no longer references `story-driver.py` or Python/PyYAML
