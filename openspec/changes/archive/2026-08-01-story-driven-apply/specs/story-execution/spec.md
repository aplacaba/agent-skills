## ADDED Requirements

### Requirement: Verify MCP server availability before running

The story-execution capability SHALL verify that the Neo4j MCP server is reachable before starting the loop. If it is not reachable, the capability SHALL print setup guidance (Docker command and opencode MCP configuration) and stop.

#### Scenario: MCP server unreachable

- **WHEN** the story execution loop starts and the Neo4j MCP server cannot be reached
- **THEN** the loop prints setup guidance and stops without modifying any files

#### Scenario: MCP server reachable

- **WHEN** the story execution loop starts and the Neo4j MCP server responds
- **THEN** the loop proceeds to the polling step

### Requirement: Poll next story from the graph

The story-execution capability SHALL continuously poll the graph for the next runnable story and implement it. After each story completes, the capability SHALL mark the story `done`, synchronize the corresponding `tasks.md` checkboxes referenced by `taskRefs`, append a compact summary to the change root's `.story-state.md`, compact context, and poll again.

#### Scenario: Story completes

- **WHEN** a story's implementation finishes and its acceptance criteria are met
- **THEN** the story status is set to `done`, the tasks referenced by its `taskRefs` are checked in `tasks.md`, and a summary line is appended to `.story-state.md`

#### Scenario: Context compaction between stories

- **WHEN** a story completes and at least one more story remains
- **THEN** the capability compacts context after recording the state, then reloads the state summary before polling for the next story

### Requirement: Handle blocked loop

The story-execution capability SHALL detect when no story is runnable but not all stories are done, and SHALL report the remaining blocked stories and stop instead of proceeding.

#### Scenario: Deadlock in dependency chain

- **WHEN** no pending story has all dependencies satisfied and not all stories are `done`
- **THEN** the loop reports the blocked stories and stops with a clear message

### Requirement: Handle complete loop

The story-execution capability SHALL detect when all stories are `done`, report completion, and suggest archiving the change.

#### Scenario: All stories complete

- **WHEN** every story for the change has status `done`
- **THEN** the loop reports completion and suggests running the archive step

### Requirement: Step with confirmation

The story-execution capability SHALL pause after each completed story and confirm with the user before implementing the next story, rather than running the full loop uninterrupted.

#### Scenario: Confirmation requested

- **WHEN** a story completes and another story is runnable
- **THEN** the loop reports the result and asks the user to confirm before starting the next story
