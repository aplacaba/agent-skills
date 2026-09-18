# Harness Tool Mapping

The canonical skills and commands in this repo use **generic** wording for tools so they are interpretable by any agent harness. This page maps the generic references to each harness's concrete tools.

The repo is distributed via thin per-harness adapters that reference the canonical content — except Pi, which needs no adapter: Pi implements the Agent Skills standard and discovers `skills/` by convention directory from the installed repo.

| Harness | Adapter | Content source |
|---|---|---|
| opencode | `.opencode/` | `skills/`, `commands/`, `agents/` (plugin injects `skills.paths`; commands/agents via symlinks) |
| Claude Code | `.claude-plugin/` | `skills/`, `commands/`, `agents/` (plugin root) |
| Codex | `.codex-plugin/` | `skills/` (manifest `skills` field) |
| Pi | none (convention discovery) | `skills/` (`pi install <source>`) |

## Generic reference → harness tool

| Generic reference (in canonical content) | opencode | Claude Code | Codex | Pi |
|---|---|---|---|---|
| skill tool | `skill` tool | Skill tool | Skill tool | skills loaded by convention from the repo's `skills/` (list with `pi skill list`); no in-session Skill tool |
| file read | `read` tool | Read | Read | `read` |
| shell / bash | `bash` tool | Bash | Bash | `bash` |
| Obsidian note read (MCP) | the Obsidian MCP server's read-note tool (registered as `obsidian`, e.g. `obsidian-mcp@2`) | read-note tool of a user-registered Obsidian MCP server | read-note tool of a user-registered Obsidian MCP server | n/a — Pi has no MCP support |
| Obsidian note read (files) | `read` tool | Read | Read | `read` |

## Notes

- **Tool permissions**: canonical content no longer declares `allowed-tools:` frontmatter. Each harness applies its own permission rules at install time. For opencode, permissions live in `permission` config (project or global).
- **`openspec` CLI**: all canonical content invokes the `openspec` CLI the same way across harnesses (`openspec status --change ...` etc.). Install the CLI once per machine.
- **story-driver**: referenced as `bb <repo>/scripts/story_driver.clj`. Requires babashka (`bb`). Path is repo-relative, so clone the repo at a known location per machine (see the setup script / install docs).
- **Obsidian MCP**: optional everywhere. opencode uses the user-registered `obsidian` server (e.g. `obsidian-mcp@2`); Claude Code and Codex need it registered by hand; Pi has no MCP support, so the story-driver skill reads vault notes via its file-read fallback. The skill uses the MCP for note reads only — writes always go through the script. Vault file tools map 1:1 across harnesses, so nothing depends on the MCP.
- **Pi**: skills load from the repo's `skills/` convention directory (`pi install <source>`, and `pi remove <source>` to uninstall). The distributed skill (`openspec-story-driver`) runs directly in the Pi session — Pi has no subagent delegation and no plan mode — and per-repo `openspec init --tools pi` supplies the stock workflow skills. There are no `/opsx-*` command aliases.
