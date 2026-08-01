## Why

The current OpenSpec apply flow (`/opsx-apply`) works through a flat list of tasks in `tasks.md`. When a change is large, an agent must hold the whole task list, design, and specs in context at once, which degrades as the change grows and causes the agent to make assumptions that were never verified. We want an execution model where a change is broken into small, fully-specified stories with dependency chains, executed one at a time with context compaction between stories, so each implementation step is unambiguous and context stays small.

## What Changes

- Add a new OpenCode skill `openspec-story-driver` and a command `/opsx-story` that implement a story-driven apply loop on top of the existing OpenSpec workflow.
- Break an OpenSpec change down into stories, each with at most 3 acceptance criteria, written so an agent can implement without making any assumptions during development.
- Create a dependency chain between stories (a story may only start when its dependencies are done).
- Store the story graph in a Neo4j database, accessed via the official Neo4j MCP server.
- Continuously poll the Neo4j MCP for the next runnable story, implement it, compact context after each completed story, then poll again until the change is complete.

## Capabilities

### New Capabilities
- `story-decomposition`: Breaks an OpenSpec change (proposal, design, specs, tasks) into stories. Each story has a title, a fully-specified description an agent can implement with zero assumptions, at most 3 acceptance criteria, and a list of dependent stories.
- `story-graph`: Stores stories and their dependency edges in Neo4j via the Neo4j MCP server, and exposes queries for ready, blocked, and completed stories.
- `story-execution`: Drives the apply loop: polls for the next ready story, hands it to the implementing agent, marks it done, compacts context, and repeats until all stories are complete.

### Modified Capabilities
<!-- No existing spec-level behavior changes. -->

## Impact

- **New files**: `.opencode/skills/openspec-story-driver/SKILL.md`, `.opencode/commands/opsx-story.md`, and a small helper script for story graph operations.
- **New dependency**: Neo4j running locally (e.g., via Docker) with the official Neo4j MCP server configured in opencode.
- **Workflow**: `/opsx-apply` remains the task-based flow; the story-driven flow is an alternative execution path layered on top of the same OpenSpec artifacts. OpenSpec artifacts are read-only inputs; no OpenSpec CLI behavior is modified.
- **No breaking changes** to the existing OpenSpec workflow or artifacts.
