## ADDED Requirements

### Requirement: Root README for humans

The repo SHALL have a root-level `README.md` that describes what the project is (identifying the opencode, Claude Code, and Codex harnesses it distributes to), its prerequisites, how to install it, how the story-driven workflow works, the repository layout, and pointers to the detailed `docs/install.md` and `docs/harness-mapping.md`.

#### Scenario: Newcomer reads the README

- **WHEN** a newcomer opens the repo
- **THEN** `README.md` explains the project purpose and names the opencode, Claude Code, and Codex harnesses, lists the prerequisites (babashka, openspec CLI, docker, git), shows the `./setup.sh` quick start, summarizes the story-driven workflow, maps the canonical directories (`skills/`, `commands/`, `agents/`, `scripts/`), and links to `docs/install.md` and `docs/harness-mapping.md`

#### Scenario: README facts match the repo

- **WHEN** the facts stated in `README.md` (prerequisite list, script paths, canonical directory layout, workflow and tool wording) are checked against the current canonical content
- **THEN** they match `setup.sh`, the skills, the commands, the scripts, and the docs

### Requirement: Root AGENTS.md with working conventions

The repo SHALL have a root-level `AGENTS.md` that states the binding conventions for AI agents working in the repo: the OpenSpec propose→apply→verify→archive workflow with no code changes without a proposal, the conventional commit style, harness-neutral wording rules for canonical content, the canonical directory layout, the exact script and test invocations, a note about the Neo4j MCP server (`mcp/neo4j-cypher` via Docker), and the rule that any future change altering a derived documentation fact SHALL update the corresponding line in the same change and verify it against the repo.

#### Scenario: Agent reads AGENTS.md

- **WHEN** an agent starts working in the repo
- **THEN** `AGENTS.md` states that code changes require a matching OpenSpec proposal first, and gives the exact invocations `bb <repo>/scripts/story_driver.clj <command> ...`, `bb scripts/test_story_driver.clj`, and `bb scripts/test_config_merge.clj`

#### Scenario: Fact-update rule stated

- **WHEN** an agent reads the documentation conventions in `AGENTS.md`
- **THEN** it instructs that a change altering a derived documentation fact (e.g., a prerequisite or script path) SHALL update the corresponding line in `README.md` or `AGENTS.md` in the same change and verify it against the repository

#### Scenario: Conventional commit format defined

- **WHEN** an agent reads the commit conventions in `AGENTS.md`
- **THEN** it defines the format `<type>(<scope>): <summary>` where `<type>` is one of `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `perf`, `build`, `<scope>` is optional, the summary is imperative, lowercase, and under 80 characters, and a body explaining why is included; it also gives the examples `feat(scripts): port story-driver CLI to babashka` and `docs: update references from Python story-driver to babashka`

#### Scenario: AGENTS.md covers harness wording, layout, and MCP note

- **WHEN** an agent reads `AGENTS.md`
- **THEN** it states that canonical content uses harness-neutral tool wording (per `docs/harness-mapping.md`), names the canonical directories (`skills/`, `commands/`, `agents/`, `scripts/`), and notes that the Neo4j MCP server runs via the `mcp/neo4j-cypher` Docker image

#### Scenario: AGENTS.md conventions match practice

- **WHEN** the conventions in `AGENTS.md` are checked against the repo history and canonical content
- **THEN** the commit subject pattern and examples match actual commits, the no-proposal rule matches the observed repository pattern, and the stated script/test invocations and MCP note match the current repo state

### Requirement: Existing files unchanged

The documentation change SHALL NOT modify any existing file or behavior. The only additions SHALL be the two new root files `README.md` and `AGENTS.md`.

#### Scenario: No existing content touched

- **WHEN** the change is applied and the repository diff is inspected
- **THEN** the product diff consists exclusively of adding `README.md` and `AGENTS.md`, and no existing file is modified
