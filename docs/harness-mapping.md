# Harness Tool Mapping

The canonical skills and commands in this repo use **generic** wording for tools so they are interpretable by any agent harness. This page maps the generic references to each harness's concrete tools.

The repo is distributed via thin per-harness adapters that reference the canonical content:

| Harness | Adapter | Content source |
|---|---|---|
| opencode | `.opencode/` | `skills/`, `commands/`, `agents/` (plugin injects `skills.paths`; commands/agents via symlinks) |
| Claude Code | `.claude-plugin/` | `skills/`, `commands/`, `agents/` (plugin root) |
| Codex | `.codex-plugin/` | `skills/` (manifest `skills` field) |

## Generic reference → harness tool

| Generic reference (in canonical content) | opencode | Claude Code | Codex |
|---|---|---|---|
| question tool | `question` tool / AskUserQuestion | AskUserQuestion | AskUserQuestion |
| todo tracking tool | `todowrite` | TodoWrite | TodoWrite |
| skill tool | `skill` tool | Skill tool | Skill tool |
| subagent / delegate to a subagent | `task` tool | Task tool with subagents (`@mention`) | Task tool / subagent |
| file read | `read` tool | Read | Read |
| file edit | `edit` tool | Edit | Edit |
| shell / bash | `bash` tool | Bash | Bash |
| Obsidian note read (MCP) | the Obsidian MCP server's read-note tool (registered as `obsidian`, e.g. `obsidian-mcp@2`) | read-note tool of a user-registered Obsidian MCP server | read-note tool of a user-registered Obsidian MCP server |
| Obsidian note read (files) | `read` tool | Read | Read |
| pull-request tool | `gh pr create` (GitHub CLI) | `gh pr create` / forge web UI | `gh pr create` / forge web UI |

## Notes

- **Tool permissions**: canonical content no longer declares `allowed-tools:` frontmatter. Each harness applies its own permission rules at install time. For opencode, permissions live in `permission` config (project or global).
- **`openspec` CLI**: all canonical content invokes the `openspec` CLI the same way across harnesses (`openspec status --change ...` etc.). Install the CLI once per machine.
- **story-driver**: referenced as `bb <repo>/scripts/story_driver.clj`. Requires babashka (`bb`). Path is repo-relative, so clone the repo at a known location per machine (see the setup script / install docs).
- **Obsidian MCP**: optional everywhere. opencode uses the user-registered `obsidian` server (e.g. `obsidian-mcp@2`); Claude Code and Codex need it registered by hand. The skill uses it for note reads only — writes always go through the script. Vault file tools map 1:1 across harnesses, so nothing depends on the MCP.
