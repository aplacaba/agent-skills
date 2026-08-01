## MODIFIED Requirements

### Requirement: Prerequisite validation

The setup script SHALL check for required tools (`bb`, `openspec`, `docker`, `git`) and SHALL print clear install guidance for any that are missing. The script SHALL exit non-zero if a hard prerequisite is missing. `python3` and PyYAML SHALL NOT be required or checked.

#### Scenario: Missing prerequisite

- **WHEN** a hard prerequisite (e.g., `bb`) is not installed
- **THEN** the script prints an install hint for that tool and exits non-zero

#### Scenario: All prerequisites present

- **WHEN** all prerequisites are installed
- **THEN** the script proceeds to the install steps

#### Scenario: Python no longer required

- **WHEN** `./setup.sh` is run on a machine without `python3` or PyYAML installed
- **THEN** the script does not check for or report `python3` or PyYAML, and completes its install steps provided `bb`, `openspec`, `docker`, and `git` are present

### Requirement: Neo4j MCP config merge

The setup script SHALL merge a Neo4j MCP server block into the global `opencode.json`, sourced from environment variables `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD`. The merge SHALL run the official `mcp-neo4j-cypher` server via its `mcp/neo4j-cypher:latest` Docker image over stdio transport, with `NEO4J_USERNAME`, `NEO4J_URI`, and `NEO4J_PASSWORD` passed as container environment variables. The merge SHALL preserve all existing keys, SHALL create a backup before editing, and SHALL be idempotent.

#### Scenario: Config merge with credentials from env

- **WHEN** `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD` are set
- **THEN** `mcp.neo4j` is added to the global config running `mcp/neo4j-cypher:latest` with those values and existing config is preserved

#### Scenario: No password provided

- **WHEN** `NEO4J_PASSWORD` is not set
- **THEN** the script skips the MCP merge, warns, and completes the rest of the install

#### Scenario: Existing neo4j block preserved

- **WHEN** `mcp.neo4j` already exists in the global config
- **THEN** the script does not overwrite it and reports the existing block

### Requirement: Per-harness install documentation

The install-setup capability SHALL document per-harness install steps for opencode (global), Claude Code, and Codex in `docs/install.md`, SHALL list babashka as a required prerequisite (with an install command), and SHALL NOT mention Python 3 or PyYAML as requirements. The script SHALL print the relevant guidance when the Claude Code or Codex adapters are detected.

#### Scenario: Documentation exists

- **WHEN** the repo is inspected
- **THEN** `docs/install.md` exists and covers opencode global, Claude Code, and Codex install steps

#### Scenario: Babashka documented as required

- **WHEN** the repo is inspected after the change
- **THEN** `docs/install.md` lists babashka (`bb`) as a required prerequisite with an install command, and no longer lists Python 3 or PyYAML as requirements

### Requirement: Git repository initialized

The repository SHALL be initialized as a git repository with a root `.gitignore` and an initial commit. The `.gitignore` SHALL NOT contain Python cache exclusions, since no Python sources remain in the repo.

#### Scenario: Repository initialized

- **WHEN** the change is applied
- **THEN** the repo is a git repository with an initial commit and a root `.gitignore` that does not list Python cache artifacts
