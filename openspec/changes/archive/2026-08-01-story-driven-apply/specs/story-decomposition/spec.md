## ADDED Requirements

### Requirement: Decompose change into fully-specified stories

The story-decomposition capability SHALL break an OpenSpec change into stories, where each story is implementable by an agent without making any assumptions during development. Each story MUST include a title, a fully-specified description naming the exact files to touch, the exact behavior to implement, and the edge cases to handle, at most 3 acceptance criteria, and a list of dependent story IDs. Stories MUST be derived from the change's `tasks.md` with `proposal.md`, `design.md`, and `specs/` used as context.

#### Scenario: Change has tasks

- **WHEN** the decomposition capability reads a change that has a `tasks.md` with pending tasks and the proposal/design/specs are complete
- **THEN** it produces one or more stories whose combined `taskRefs` exactly cover every task, and each story includes a description, at most 3 acceptance criteria, and a dependency list

#### Scenario: Task requires zero-assumption specification

- **WHEN** a task mentions a file path or behavior that is ambiguous
- **THEN** the story description MUST resolve that ambiguity by naming the exact file path and the exact expected behavior before the story is considered decomposable

### Requirement: Enforce story acceptance criterion limit

The decomposition capability SHALL ensure every story has at most 3 acceptance criteria. If a task set requires more than 3 criteria, the capability MUST split it into multiple stories.

#### Scenario: Task set exceeds three criteria

- **WHEN** a candidate story would require 4 or more acceptance criteria
- **THEN** the decomposition splits it into additional stories so no story exceeds 3 acceptance criteria

### Requirement: Provide human-readable story list

The decomposition capability SHALL write a human-readable `stories.md` file into the change root for review before any story is seeded into the graph. The file MUST list every story with its id, title, description, acceptance criteria, and dependencies.

#### Scenario: Review before seeding

- **WHEN** decomposition completes
- **THEN** a `stories.md` file exists in the change root listing every story and its dependencies for review
