# openspec-tooling

OpenSpec agent tooling for AI coding harnesses: the OpenSpec workflow skills
(`/opsx-*` commands), a story-driven apply workflow backed by a Neo4j story
graph, and an openspec change reviewer agent. The content is distributed from
one canonical root (`skills/`, `commands/`, `agents/`, `scripts/`) to three
harnesses — **opencode**, **Claude Code**, and **Codex** — via thin adapters, so
there is no duplicated content and no cross-harness drift.

## Prerequisites

- [Babashka](https://babashka.org) (`bb`) — tested minimum v1.13.219
- [OpenSpec CLI](https://github.com/Fission-AI/OpenSpec) (`npm i -g openspec`)
- Docker (for the Neo4j MCP server via the `mcp/neo4j-cypher:latest` image)
- Git

You also need a running Neo4j instance (or connection details to a deployed
one); see [Install](docs/install.md) for the `NEO4J_*` environment variables.

## Quick start (opencode)

```bash
./setup.sh
```

The script checks prerequisites, symlinks `skills/`, `commands/`, `agents/`,
and the opencode plugin into `~/.config/opencode/`, and merges a Neo4j MCP
server block into your opencode config. It is safe to re-run. Restart opencode
afterwards, then use the `/opsx-*` commands.

For Claude Code and Codex install steps, see [Install](docs/install.md).

## How the story-driven workflow works

1. **Propose** — `/opsx-propose` creates a change with proposal, design, specs,
   and tasks.
2. **Decompose** — `/opsx-story` turns the change's tasks into stories with
   acceptance criteria and dependencies, seeds them into a Neo4j story graph,
   and shows you `stories.md` for review.
3. **Apply** — `/opsx-apply` works through the tasks; each story is implemented
   by an agent, verified against its acceptance criteria, and its tasks are
   checked off.
4. **Archive** — `/opsx-archive` syncs the delta specs into the main specs and
   moves the change into `openspec/changes/archive/`, then pushes the change
   branch and opens a pull request against the default branch.

The mechanical parts (parsing tasks, validating story definitions, generating
`stories.md` + `story-seed.cypher`, toggling task checkboxes, appending state)
are handled by the babashka script `scripts/story_driver.clj`.

## Branching strategy

Proposed changes use a worktree-per-change git workflow:

- Each proposed OpenSpec change gets its own worktree at `.worktrees/<name>/`
  on branch `change/<name>`, created when the change is proposed. All change
  work happens inside the worktree; the main checkout stays on the default
  branch and is never switched.
- The default branch receives proposed-change commits only when the change's
  pull request merges (bug fixes are exempt and commit directly on the default
  branch).
- The archive step commits remaining work, pushes the change branch, opens a
  pull request against the default branch, and removes the worktree. The
  branch is kept until the pull request merges; merging happens through the
  pull request, not in the archive flow.

## Repository layout

| Path | Contents |
|---|---|
| `skills/` | Canonical skills (openspec workflow, propose, apply, archive, explore, sync, story driver) |
| `commands/` | Canonical `/opsx-*` command definitions |
| `agents/` | The openspec change reviewer agent |
| `scripts/` | Babashka helper scripts (`story_driver.clj`, `config-merge.clj`) + test suites |
| `docs/` | [Install](docs/install.md) and [harness tool mapping](docs/harness-mapping.md) |
| `.worktrees/` | Per-change git worktrees (gitignored) |
| `openspec/specs/` | Main OpenSpec specifications |
| `openspec/changes/` | Active changes; `archive/` holds completed ones |
| `.opencode/` | opencode adapter (plugin + command/agent symlinks) |
| `.claude-plugin/` | Claude Code adapter manifest |
| `.codex-plugin/` | Codex adapter manifest |
| `setup.sh` | Global installer for opencode |

## Documentation

- [Install](docs/install.md) — full install for all three harnesses, Neo4j MCP
  environment variables, uninstall.
- [Harness tool mapping](docs/harness-mapping.md) — how the generic tool
  wording in canonical content maps to each harness's concrete tools.
- [AGENTS.md](AGENTS.md) — binding conventions for AI agents working in this
  repo (workflow, commits, layout, script invocations).
