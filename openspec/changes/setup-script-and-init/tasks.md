## 1. Setup script

- [ ] 1.1 Write `setup.sh` with `set -euo pipefail`, repo-path resolution via Python (portable across Linux/macOS), and prerequisite checks (`python3`, PyYAML, `openspec`, `node`, `git`) with clear hints
- [ ] 1.2 Implement idempotent symlinking of skills into `~/.config/opencode/skill/`, commands into `~/.config/opencode/command/`, agent into `~/.config/opencode/agent/`, and the plugin into `~/.config/opencode/plugins/`
- [ ] 1.3 Implement Neo4j MCP config merge into `~/.config/opencode/opencode.json` from `NEO4J_URI`/`NEO4J_USER`/`NEO4J_PASSWORD` with backup, JSON-preserving merge, and idempotency (skip if `mcp.neo4j` exists or password missing)
- [ ] 1.4 Print per-harness install guidance for Claude Code and Codex at the end of the run

## 2. Documentation

- [ ] 2.1 Write `docs/install.md` covering prerequisites, `./setup.sh` usage, env vars, per-harness install commands (opencode global, Claude Code, Codex), and verification

## 3. Repository initialization

- [ ] 3.1 Create root `.gitignore` excluding `__pycache__/` and `*.pyc` (preserving existing `.opencode/.gitignore`)
- [ ] 3.2 Run `git init`, stage the tree, and create the initial commit

## 4. Verification

- [ ] 4.1 Run `./setup.sh` and confirm skills, commands, agents, and plugin are symlinked into the global opencode config
- [ ] 4.2 Confirm re-running `./setup.sh` is idempotent (no duplicate symlinks, config untouched)
- [ ] 4.3 Confirm `git log` shows the initial commit and `git status` is clean
