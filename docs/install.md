# Install

This repo provides the OpenSpec agent tooling: openspec workflow skills, the story-driven apply workflow (backed by a Neo4j story graph), and the openspec change reviewer agent. It is distributed via thin per-harness adapters from one canonical content root (`skills/`, `commands/`, `agents/`, `scripts/`).

## Prerequisites

- Babashka (`bb`) — tested minimum: **v1.13.219** (install: `curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && chmod +x install && ./install`)
- OpenSpec CLI (`npm i -g openspec`)
- Docker (for the Neo4j MCP server via the `mcp/neo4j-cypher:latest` image)
- Git
- A running Neo4j instance (or the connection details to your deployed one)

## opencode (global install)

Run the setup script from the repo root:

```bash
./setup.sh
```

This:

1. Checks prerequisites and prints install hints for anything missing.
2. Symlinks `skills/`, `commands/`, `agents/`, and the opencode plugin into `~/.config/opencode/` so opencode discovers everything globally.
3. Merges a Neo4j MCP server block into `~/.config/opencode/opencode.json` (creating a `.bak` backup first). It is idempotent and never overwrites an existing `mcp.neo4j` block.
4. Prints Claude Code and Codex install guidance.

The script is safe to re-run. If you move the repo, re-run it to refresh the absolute symlinks.

### Neo4j MCP environment variables

The MCP merge is driven by environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `NEO4J_URI` | `bolt://localhost:7687` | Bolt URI of your Neo4j instance |
| `NEO4J_USER` | `neo4j` | Username |
| `NEO4J_PASSWORD` | *(required)* | Password; if unset the MCP merge is skipped |

Example:

```bash
NEO4J_PASSWORD=secret ./setup.sh
```

The password is substituted into the config at install time. If you prefer it not to live in the config file, omit `NEO4J_PASSWORD`, then add the MCP block manually using opencode's `{env:...}` interpolation instead.

After setup, **restart opencode** so the plugin, skills, commands, agents, and MCP server load.

## Claude Code

The repo's `.claude-plugin/marketplace.json` defines marketplace `openspec-tooling-dev` containing plugin `openspec-tooling`. Install locally:

```bash
# inside a Claude Code session
/plugin marketplace add /abs/path/to/my-agent-skill
/plugin install openspec-tooling@openspec-tooling-dev
```

Claude Code discovers `skills/`, `commands/`, and `agents/` from the plugin root. When the repo is pushed to GitHub, use the repo URL instead of the local path, or publish to the official marketplace.

### Neo4j MCP server

The plugin ships the MCP server: `.mcp.json` in the plugin root declares a `neo4j` stdio server, and Claude Code starts it automatically when the plugin is enabled. No `claude mcp add` needed.

It carries no credentials. The config reads them from the environment, with defaults for everything except the password:

| Variable | Default | Purpose |
|---|---|---|
| `NEO4J_URI` | `bolt://localhost:7687` | Bolt URI of your Neo4j instance |
| `NEO4J_USERNAME` | `neo4j` | Username |
| `NEO4J_PASSWORD` | *(empty)* | Password; the server starts but fails to authenticate if unset |
| `NEO4J_DATABASE` | `neo4j` | Database name |

Export them wherever you launch Claude Code, e.g. in `~/.zshrc`:

```bash
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USERNAME=neo4j
export NEO4J_PASSWORD=secret
```

Notes:

- Note the variable name: the MCP image reads `NEO4J_USERNAME`, while `setup.sh` takes `NEO4J_USER` as its input variable for the opencode merge.
- Credentials are passed to the container by `docker run -e VAR` name-only passthrough, so the password never appears in the process arguments.
- If Neo4j runs in Docker on the same host, use `bolt://host.docker.internal:7687` — inside the MCP container, `localhost` is the container itself.
- The `mcp/neo4j-cypher:latest` image is pulled on first connect, so the first startup is slower.
- Working in this repo directly rather than installing the plugin? The same `.mcp.json` is picked up as a project-scoped server, and Claude Code asks you to approve it once.
- Restart the session after changing the variables; MCP servers load at startup.

To reuse the values `setup.sh` already wrote for opencode, read them out of `~/.config/opencode/opencode.json` under `mcp.neo4j.command`.

#### Overriding with a personal config

To point at a different instance without touching the repo — or to avoid exporting variables — register a user-scoped server, which takes precedence over the project-scoped `.mcp.json` of the same name:

```bash
claude mcp add-json neo4j '{
  "type": "stdio",
  "command": "docker",
  "args": ["run","-i","--rm",
    "-e","NEO4J_URI=bolt://localhost:7687",
    "-e","NEO4J_USERNAME=neo4j",
    "-e","NEO4J_PASSWORD=secret",
    "-e","NEO4J_DATABASE=neo4j",
    "-e","NEO4J_TRANSPORT=stdio",
    "mcp/neo4j-cypher:latest"]
}' --scope user
```

This writes the password in plaintext to `~/.claude.json`, the same as the opencode merge does.

## Codex

The repo's `.codex-plugin/plugin.json` registers plugin `openspec-tooling` with `"skills": "./skills/"`. Install:

```bash
# inside the Codex CLI
/plugins            # open plugin search
openspec-tooling    # search
Install Plugin
```

For a local repo path, register it via the app's Plugins sidebar or `codex plugin add <abs/path/to/repo>`.

## Verifying the install

- **opencode**: restart opencode, then ask it to list skills or run `/opsx-story`. Confirm the Neo4j MCP server appears in the MCP list (the story-driven skill's first phase verifies connectivity).
- **Claude Code**: `/plugin` shows `openspec-tooling`; the `openspec-*` skills and `/opsx-*` commands are available. `claude mcp list` shows `plugin:openspec-tooling:neo4j`. Note that its `✔ Connected` only means the MCP server process started — the server connects to Neo4j lazily, so a bad URI or password still shows as connected and fails on the first query. To actually verify the credentials, run a Cypher query (`RETURN 1 AS ok`), which is what the story-driver skill's Phase 0 does.
- **Codex**: the `openspec-*` skills are discoverable.

## Uninstall

Remove the symlinks `setup.sh` created:

```bash
rm -f ~/.config/opencode/plugins/openspec-tooling.js \
      ~/.config/opencode/agent/openspec-reviewer.md
rm -rf ~/.config/opencode/skill/openspec-* ~/.config/opencode/command/opsx-*
```

If you want to revert the config merge, restore `~/.config/opencode/opencode.json.bak`.

For Claude Code, `/plugin uninstall openspec-tooling@openspec-tooling-dev` removes the plugin and its bundled `neo4j` MCP server together. If you also registered a user-scoped override, drop it separately:

```bash
claude mcp remove neo4j --scope user
```
