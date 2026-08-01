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
| Cypher MCP tool | the Neo4j MCP server's Cypher tool | the Neo4j MCP server's Cypher tool | the Neo4j MCP server's Cypher tool |

## Notes

- **Tool permissions**: canonical content no longer declares `allowed-tools:` frontmatter. Each harness applies its own permission rules at install time. For opencode, permissions live in `permission` config (project or global).
- **`openspec` CLI**: all canonical content invokes the `openspec` CLI the same way across harnesses (`openspec status --change ...` etc.). Install the CLI once per machine.
- **story-driver.py**: referenced as `<repo>/scripts/story-driver.py`. Requires Python 3 with PyYAML. Path is repo-relative, so clone the repo at a known location per machine (see the setup script / install docs).
