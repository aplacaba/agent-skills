## Why

The repo now holds the canonical agent tooling with per-harness adapters, but there is no automated way to install it on a new machine and the repo is not a git repository, so there is no version history or remote. We want a `setup.sh` that installs the tooling into opencode (and optionally registers Claude Code / Codex adapters), checks dependencies, and merges the Neo4j MCP config, plus a git repository initialized for distribution.

## What Changes

- Add `setup.sh` at the repo root that:
  - Verifies prerequisites (`python3`, PyYAML, `openspec`, `node`, `git`).
  - Symlinks canonical `skills/`, `commands/`, `agents/`, and the opencode plugin into `~/.config/opencode/` so opencode discovers everything globally.
  - Merges a Neo4j MCP server block into `~/.config/opencode/opencode.json`, using environment-variable interpolation for credentials (no secrets in the repo).
  - Optionally prints per-harness install commands for Claude Code and Codex.
  - Is idempotent (safe to re-run).
- Add `docs/install.md` with per-harness install steps (opencode global, Claude Code, Codex).
- **Initialize the repo as a git repository** with an initial commit and a root `.gitignore` (excluding `node_modules`, `__pycache__`, and `openspec/changes/archive` artifacts as appropriate).
- **BREAKING**: none — this is additive.

## Capabilities

### New Capabilities
- `install-setup`: Installs the canonical tooling into the opencode global config via a single idempotent `setup.sh`, checks prerequisites, symlinks skills/commands/agents/plugin, and merges the Neo4j MCP config from environment variables.

### Modified Capabilities
<!-- No existing spec-level behavior changes. -->

## Impact

- **New files**: `setup.sh`, `docs/install.md`, root `.gitignore`.
- **New behavior**: `setup.sh` edits `~/.config/opencode/opencode.json` (global config) — outside the repo. It must be safe, idempotent, and never overwrite unrelated config.
- **New dependency**: none beyond existing prerequisites; Neo4j MCP credentials come from env vars (`NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`).
- **Repository state**: `git init` + initial commit; no remote configured in this change.
