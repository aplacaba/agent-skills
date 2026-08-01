## ADDED Requirements

### Requirement: Canonical content root

The distribution layout SHALL keep harness-agnostic content in canonical root directories: `skills/`, `commands/`, `agents/`, and `scripts/`. Each harness adapter SHALL reference these canonical paths rather than duplicating content.

#### Scenario: Canonical locations exist

- **WHEN** the repo is checked out
- **THEN** `skills/`, `commands/`, `agents/`, and `scripts/` exist at the repo root and contain all skills, commands, agents, and helper scripts respectively

#### Scenario: No duplicated content

- **WHEN** a harness adapter is installed
- **THEN** the canonical content is registered by reference and no file content is copied into adapter directories

### Requirement: Harness-agnostic content

Canonical skills and commands SHALL NOT contain harness-specific tool tokens such as `allowed-tools:` frontmatter or opencode-specific tool names. Where tool references are required, content SHALL use generic phrasing and SHALL reference a harness tool-mapping note.

#### Scenario: Frontmatter is harness-neutral

- **WHEN** a canonical SKILL.md is inspected
- **THEN** it does not contain an `allowed-tools:` field

#### Scenario: Tool references are generic

- **WHEN** a canonical command or skill references an interactive prompt or tool
- **THEN** it uses generic wording (e.g., "the question tool") rather than a harness-specific tool name

### Requirement: opencode adapter

The opencode adapter SHALL make all canonical skills, commands, and agents discoverable in opencode. The opencode plugin SHALL inject the canonical `skills` directory into the opencode config's `skills.paths`. Commands and agents SHALL be registered via `.opencode/commands` and `.opencode/agents` referencing the canonical `commands/` and `agents/` directories.

#### Scenario: Skills discoverable in opencode

- **WHEN** the opencode plugin loads
- **THEN** `config.skills.paths` includes the canonical `skills` directory and opencode discovers every skill

#### Scenario: Commands and agents discoverable in opencode

- **WHEN** opencode scans `.opencode/`
- **THEN** it discovers all commands and agents through the references to the canonical `commands/` and `agents/` directories

### Requirement: Claude Code adapter

The Claude Code adapter SHALL provide a `.claude-plugin/plugin.json` manifest and a marketplace manifest that register the canonical skills, commands, and agents for Claude Code.

#### Scenario: Claude Code registers content

- **WHEN** a user installs the plugin from the repo
- **THEN** Claude Code discovers the canonical skills, commands, and agents

### Requirement: Codex adapter

The Codex adapter SHALL provide a `.codex-plugin/plugin.json` manifest whose `skills` field points at the canonical `skills` directory.

#### Scenario: Codex registers skills

- **WHEN** a user installs the Codex plugin from the repo
- **THEN** Codex discovers the canonical skills via the manifest's skills path

### Requirement: Existing opencode workflow preserved

The move to the canonical layout SHALL NOT break the existing opencode workflow: `/opsx-*` commands and the `openspec-story-driver` skill MUST still work in opencode after the change.

#### Scenario: Commands still work

- **WHEN** the change is applied
- **THEN** `/opsx-apply`, `/opsx-propose`, `/opsx-archive`, `/opsx-sync`, `/opsx-explore`, and `/opsx-story` are discoverable in opencode

#### Scenario: Story-driver script path updated

- **WHEN** the story-driver script moves to the canonical `scripts/` directory
- **THEN** the `openspec-story-driver` skill references the new script path and the script still runs
