# story-execution

## Purpose

Drives the story-driven apply loop: polls the Neo4j story graph for the next runnable story, implements it, marks it done, compacts context, and repeats until the change is complete or blocked.

## Requirements

### Requirement: Verify MCP server availability before running

The story-execution capability SHALL verify that the Neo4j MCP server is reachable before starting the loop. If it is not reachable, the capability SHALL print setup guidance (Docker command and opencode MCP configuration) and stop.

#### Scenario: MCP server unreachable

- **WHEN** the story execution loop starts and the Neo4j MCP server cannot be reached
- **THEN** the loop prints setup guidance and stops without modifying any files

#### Scenario: MCP server reachable

- **WHEN** the story execution loop starts and the Neo4j MCP server responds
- **THEN** the loop proceeds to the polling step

### Requirement: Poll next story from the graph

The story-execution capability SHALL continuously poll the graph for the next runnable story of the change (within the change's project) and implement it. After each story completes, the capability SHALL mark the story `done`, synchronize the corresponding `tasks.md` checkboxes referenced by `taskRefs`, append a compact summary to the change root's `.story-state.md`, compact context, and poll again. All story-graph queries (polling, readiness, status transitions, blocked detection, completion checks, story selection) SHALL be scoped to the change's `{change, project}` pair, including direct `Story`-node updates which SHALL match `{id, change, project}`.

#### Scenario: Story completes

- **WHEN** a story's implementation finishes and its acceptance criteria are met
- **THEN** the story status is set to `done` (matching its `id`, `change`, and `project`), the tasks referenced by its `taskRefs` are checked in `tasks.md`, and a summary line is appended to `.story-state.md`

#### Scenario: Context compaction between stories

- **WHEN** a story completes and at least one more story remains
- **THEN** the capability compacts context after recording the state, then reloads the state summary before polling for the next story

#### Scenario: Queries are change- and project-scoped

- **WHEN** the execution loop polls, updates status, or checks completion for a change
- **THEN** every query filters by the change's `change` and `project` properties, so stories of other changes or projects are never selected or modified

### Requirement: Handle blocked loop

The story-execution capability SHALL detect when no story of the change (within the change's project) is runnable but not all of that change's stories are done, and SHALL report the remaining blocked stories and stop instead of proceeding.

#### Scenario: Deadlock in dependency chain

- **WHEN** no pending story of the change has all dependencies satisfied and not all of that change's stories are `done`
- **THEN** the loop reports the blocked stories and stops with a clear message

### Requirement: Handle complete loop

The story-execution capability SHALL detect when all stories of the change (within the change's project) are `done`, report completion, and suggest archiving the change.

#### Scenario: All stories complete

- **WHEN** every story for the change has status `done`
- **THEN** the loop reports completion and suggests running the archive step

### Requirement: Step with confirmation

The story-execution capability SHALL pause after each completed story and confirm with the user before implementing the next story, rather than running the full loop uninterrupted. Auto mode SHALL be activated only by passing the `--auto` flag to the story command; no environment variable or configuration setting SHALL activate it. When `--auto` is passed, the capability SHALL skip the inter-story confirmation and continue automatically; auto mode SHALL NOT bypass any other stop — Phase 1 `stories.md` approval, ambiguous-story pauses, MCP-unreachable stops, blocked runs, and completion all behave as in interactive mode. Interactive confirmation SHALL remain the default.

#### Scenario: Confirmation requested

- **WHEN** a story completes and another story is runnable, in interactive mode
- **THEN** the loop reports the result and asks the user to confirm before starting the next story

#### Scenario: Auto-approve skips confirmation

- **WHEN** a story completes and another story is runnable, in auto-approve mode
- **THEN** the loop proceeds to the next story without pausing for confirmation

#### Scenario: Auto-approve still stops on other conditions

- **WHEN** `--auto` is passed and the user has not yet approved `stories.md`, a story is ambiguous, the MCP server is unreachable, no story is runnable, or all stories are complete
- **THEN** the loop stops or reports exactly as in interactive mode
