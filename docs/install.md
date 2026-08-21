# Install

This repo provides the OpenSpec agent tooling: openspec workflow skills, the story-driven apply workflow (backed by an Obsidian-vault story graph), and the openspec change reviewer agent. It is distributed via thin per-harness adapters from one canonical content root (`skills/`, `commands/`, `agents/`, `scripts/`).

## Prerequisites

- Babashka (`bb`) — tested minimum: **v1.13.219** (install: `curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install && chmod +x install && ./install`)
- OpenSpec CLI (`npm i -g openspec`)
- Git
- An Obsidian vault (a directory with an `.obsidian` folder). The story graph lives there as markdown notes; the app itself does not need to be running.

## Story graph storage

The story-driven workflow writes story notes into a vault resolved as: `--vault <path>` flag → `OBSIDIAN_VAULT` environment variable → `~/obsidian/obsidian`. Set `OBSIDIAN_VAULT` if your vault lives elsewhere:

```bash
export OBSIDIAN_VAULT=/home/you/Documents/MyVault
```

The vault root must exist; the tooling creates only the `Stories/` and `Projects/` descendants.

### Obsidian MCP (optional)

No MCP server is required. If you want an Obsidian MCP for read access (the skill uses its read tools when present), register one per harness:

- **opencode** — add an `mcp.obsidian` block to `~/.config/opencode/opencode.json` (Node.js 22+ required):

```jsonc
{
  "mcp": {
    "obsidian": {
      "type": "local",
      "command": ["npx", "-y", "obsidian-mcp@2", "serve",
                  "--vault", "notes=/abs/path/to/vault"]
    }
  }
}
```

- **Claude Code** — `claude mcp add obsidian -- npx -y obsidian-mcp@2 serve --vault notes=/abs/path/to/vault`
- **Codex** — register the server by hand in the Codex MCP settings with the same command.

The server reads and writes vault files directly; Obsidian does not need to be open. In this workflow it is read-only by convention: the story-driver skill never writes notes through MCP tools.

## opencode (global install)

Run the setup script from the repo root:

```bash
./setup.sh
```

This:

1. Checks prerequisites and prints install hints for anything missing.
2. Symlinks `skills/`, `commands/`, `agents/`, and the opencode plugin into `~/.config/opencode/` so opencode discovers everything globally.
3. Verifies the story-graph vault (warns when the default `~/obsidian/obsidian` is missing; honor `OBSIDIAN_VAULT`).
4. Prints Claude Code and Codex install guidance.

The script is safe to re-run. If you move the repo, re-run it to refresh the absolute symlinks.

After setup, **restart opencode** so the plugin, skills, commands, and agents load.

## Claude Code

The repo's `.claude-plugin/marketplace.json` defines marketplace `openspec-tooling-dev` containing plugin `openspec-tooling`. Install locally:

```bash
# inside a Claude Code session
/plugin marketplace add /abs/path/to/my-agent-skill
/plugin install openspec-tooling@openspec-tooling-dev
```

Claude Code discovers `skills/`, `commands/`, and `agents/` from the plugin root. When the repo is pushed to GitHub, use the repo URL instead of the local path, or publish to the official marketplace.

The plugin ships no MCP server. The story-driven workflow reads and writes vault files directly; register an Obsidian MCP (above) only if you want read tools.

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

- **opencode**: restart opencode, then ask it to list skills or run `/opsx-story`. The story-driven skill's Phase 0 verifies the vault; if you registered an Obsidian MCP, confirm it appears in the MCP list.
- **Claude Code**: `/plugin` shows `openspec-tooling`; the `openspec-*` skills and `/opsx-*` commands are available.
- **Codex**: the `openspec-*` skills are discoverable.

## Uninstall

Remove the symlinks `setup.sh` created:

```bash
rm -f ~/.config/opencode/plugins/openspec-tooling.js \
      ~/.config/opencode/agent/openspec-reviewer.md
rm -rf ~/.config/opencode/skill/openspec-* ~/.config/opencode/command/opsx-*
```

For Claude Code, `/plugin uninstall openspec-tooling@openspec-tooling-dev` removes the plugin. If you registered an Obsidian MCP separately, drop it with the harness's own MCP removal command. The vault notes themselves are yours to keep or delete.
