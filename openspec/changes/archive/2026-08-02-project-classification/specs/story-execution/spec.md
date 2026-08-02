## MODIFIED Requirements

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
