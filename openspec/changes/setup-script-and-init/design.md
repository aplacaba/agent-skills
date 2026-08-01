## Context

The repo (`my-agent-skill`) holds canonical agent tooling under `skills/`, `commands/`, `agents/`, `scripts/` with per-harness adapters (`.opencode/`, `.claude-plugin/`, `.codex-plugin/`). Installing on a new machine currently means manually symlinking into the global opencode config and adding the Neo4j MCP block. The repo is also not yet a git repository.

## Goals / Non-Goals

**Goals:**
- One command (`./setup.sh`) installs the tooling globally for opencode on a fresh machine.
- Setup is idempotent and never destroys unrelated global config.
- Neo4j MCP credentials come from environment variables, never committed.
- Per-harness (Claude Code, Codex) install guidance is printed and documented.
- Repo is initialized as a git repository with an initial commit.

**Non-Goals:**
- Automating the Claude Code / Codex install steps (those are interactive/plugin-store operations) — the script prints instructions and `docs/install.md` documents them.
- Configuring a git remote or pushing anywhere.
- Writing a full test suite; verification is by running the script and checking results.

## Decisions

### D1. Single bash script at repo root: `setup.sh`
Bash keeps the script dependency-free on the target machine (no Python/Node needed to install Python/Node tooling). It uses `set -euo pipefail` and is idempotent.
- *Alternative considered:* a Python installer. Rejected — Python may not be present yet; the script checks for it.

### D2. Symlinks for skills/commands/agents/plugin into global opencode config
`setup.sh` creates:
- `~/.config/opencode/plugins/openspec-tooling.js` → `<repo>/.opencode/plugins/openspec-tooling.js`
- `~/.config/opencode/skill/<name>` → `<repo>/skills/<name>` for each skill
- `~/.config/opencode/command/*.md` → `<repo>/commands/*.md`
- `~/.config/opencode/agent/openspec-reviewer.md` → `<repo>/agents/openspec-reviewer.md`

Each symlink is created only if not already present (or refreshed if the target differs). Directory names: opencode accepts both `skill(s)` and `command(s)`/`agent(s)`; the script uses singular dirs (`skill`, `command`, `agent`).
- *Why symlinks:* single source of truth, `git pull` updates propagate automatically.

### D3. Neo4j MCP config merge into `~/.config/opencode/opencode.json`
The script reads env vars `NEO4J_URI` (default `bolt://localhost:7687`), `NEO4J_USER` (default `neo4j`), `NEO4J_PASSWORD` (required, no default). It merges a `mcp.neo4j` local-server block into the global config using JSON manipulation:
```jsonc
"mcp": {
  "neo4j": {
    "type": "local",
    "command": ["npx", "-y", "@neo4j/mcp-server", "--uri", "<uri>", "--database", "neo4j", "--username", "<user>", "--password", "<pass>"]
  }
}
```
- The merge preserves all existing keys and only sets `mcp.neo4j`. If `mcp` or `mcp.neo4j` already exist, it reports and leaves the existing block (idempotent).
- *Why not write a template file:* the script edits the live config so it stays consistent with the user's other settings.
- `$schema` is preserved if present, or added.
- *Alternative considered:* `{env:...}` interpolation. opencode supports `{env:VAR}` in config strings, but the script instead substitutes values at install time so the config works even without the env vars at opencode launch; both are acceptable, install-time substitution chosen for simplicity.

### D4. Prerequisite checks
`setup.sh` checks `python3`, `python3 -c "import yaml"`, `openspec`, `node`, `git` and prints clear install hints for any missing tool, then exits non-zero for missing hard prerequisites. Missing PyYAML is a warning (pip install pyyaml) — still non-fatal but printed.

### D5. Git initialization
`git init`, add a root `.gitignore`, and create an initial commit. Root `.gitignore`:
```
__pycache__/
*.pyc
```
plus preserve the existing `.opencode/.gitignore` behavior (node_modules is already ignored there). The `openspec/changes/archive/` directory is intentionally committed (it holds spec history) — not ignored.
- No remote configured (non-goal).

### D6. `docs/install.md`
Documents: prerequisites, `./setup.sh` usage, env vars, per-harness install commands (opencode global, Claude Code `/plugin marketplace add`, Codex `/plugins`), and verification.

## Risks / Trade-offs

- **Symlink targets break if repo moves** → Symlinks are absolute to the repo location; moving the repo requires re-running `setup.sh`. Documented in `docs/install.md`. Risk → the script refreshes symlinks on each run.
- **Editing global config corrupts existing setup** → The merge is read-modify-write with JSON parsing and only touches `mcp.neo4j`/`$schema`; a backup of `opencode.json` is written before edit. Risk → mitigation is the backup file.
- **Password in plaintext in global config** → Inherent to local MCP servers; documented. Risk → could use `{env:NEO4J_PASSWORD}` interpolation instead (left as an option; default is install-time substitution).
- **Bash portability (macOS vs Linux)** → `readlink -f` differs; script uses `python3 -c os.path.realpath` for canonicalizing the repo path, avoiding BSD/GNU `readlink` differences.

## Migration Plan

1. Write `setup.sh`, `docs/install.md`, root `.gitignore`.
2. Run `setup.sh` locally to verify (it will install globally on this machine and merge the Neo4j MCP config).
3. `git init`, stage, and create the initial commit.
4. Rollback: removing the created symlinks and restoring the config backup is trivial and documented.

## Open Questions

- Whether the user wants install-time password substitution (plaintext in config) or `{env:NEO4J_PASSWORD}` interpolation (requires env at opencode launch). Default chosen: install-time substitution, with interpolation as an option.
- Which git user identity to use for the initial commit (script will prompt or use global `git config`).
