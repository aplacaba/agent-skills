# install-setup

## Purpose

Installs the canonical tooling into the global opencode config via a single idempotent `setup.sh`, checks prerequisites, symlinks skills/commands/agents/plugin, and merges the Neo4j MCP config from environment variables.

## Requirements

### Requirement: Single-command global install for opencode

The install-setup capability SHALL provide a `setup.sh` script at the repo root that installs the canonical tooling into the global opencode config (`~/.config/opencode/`) with a single invocation. The script SHALL symlink skills, commands, agents, and the opencode plugin into their global opencode directories.

#### Scenario: Fresh machine install

- **WHEN** `./setup.sh` is run on a machine without existing opencode tooling
- **THEN** all canonical skills, commands, agents, and the plugin become discoverable in opencode globally

#### Scenario: Re-running setup is safe

- **WHEN** `./setup.sh` is run a second time
- **THEN** no duplicate or broken symlinks are created and no unrelated config is changed

### Requirement: Prerequisite validation

The setup script SHALL check for required tools (`python3`, `openspec`, `node`, `git`) and the PyYAML Python package, and SHALL print clear install guidance for any that are missing. The script SHALL exit non-zero if a hard prerequisite is missing.

#### Scenario: Missing prerequisite

- **WHEN** a hard prerequisite (e.g., `openspec`) is not installed
- **THEN** the script prints an install hint for that tool and exits non-zero

#### Scenario: All prerequisites present

- **WHEN** all prerequisites are installed
- **THEN** the script proceeds to the install steps

### Requirement: Neo4j MCP config merge

The setup script SHALL merge a Neo4j MCP server block into the global `opencode.json`, sourced from environment variables `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD`. The merge SHALL preserve all existing keys, SHALL create a backup before editing, and SHALL be idempotent.

#### Scenario: Config merge with credentials from env

- **WHEN** `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD` are set
- **THEN** `mcp.neo4j` is added to the global config with those values and existing config is preserved

#### Scenario: No password provided

- **WHEN** `NEO4J_PASSWORD` is not set
- **THEN** the script skips the MCP merge, warns, and completes the rest of the install

#### Scenario: Existing neo4j block preserved

- **WHEN** `mcp.neo4j` already exists in the global config
- **THEN** the script does not overwrite it and reports the existing block

### Requirement: Per-harness install documentation

The install-setup capability SHALL document per-harness install steps for opencode (global), Claude Code, and Codex in `docs/install.md`, and SHALL print the relevant guidance when the Claude Code or Codex adapters are detected.

#### Scenario: Documentation exists

- **WHEN** the repo is inspected
- **THEN** `docs/install.md` exists and covers opencode global, Claude Code, and Codex install steps

### Requirement: Git repository initialized

The repository SHALL be initialized as a git repository with a root `.gitignore` (excluding `__pycache__`/`*.pyc`) and an initial commit.

#### Scenario: Repository initialized

- **WHEN** the change is applied
- **THEN** the repo is a git repository with an initial commit and a root `.gitignore` covering Python cache artifacts
