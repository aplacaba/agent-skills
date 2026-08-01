# story-graph

## Purpose

Stores stories and their dependency edges in a Neo4j database, accessed exclusively through the Neo4j MCP server's Cypher execution tool.

## Requirements

### Requirement: Store story graph in Neo4j via MCP

The story-graph capability SHALL store stories and their dependency edges in a Neo4j database, and SHALL access the database exclusively through the Cypher execution tool exposed by the Neo4j MCP server. The graph SHALL model a `Change` node, `Story` nodes with properties `id`, `title`, `description`, `acceptanceCriteria`, `status`, and `taskRefs`, and `DEPENDS_ON` edges between stories.

#### Scenario: Seed stories into graph

- **WHEN** the story graph receives a seeded change
- **THEN** a `Change` node and one `Story` node per story are created in the database, with `DEPENDS_ON` edges representing each story's dependency list

#### Scenario: Status lifecycle

- **WHEN** a story is selected for implementation
- **THEN** its status transitions from `pending` to `in_progress`; when implementation completes it transitions to `done`

### Requirement: Query readiness from the graph

The story-graph capability SHALL expose a query that returns the next runnable story: a story whose status is `pending` and where none of its `DEPENDS_ON` targets have a status other than `done`.

#### Scenario: Dependencies not satisfied

- **WHEN** a pending story has at least one `DEPENDS_ON` target that is not `done`
- **THEN** that story is not returned as runnable

#### Scenario: All dependencies satisfied

- **WHEN** a pending story has all of its `DEPENDS_ON` targets set to `done`
- **THEN** that story is returned as the next runnable story

#### Scenario: Multiple runnable stories

- **WHEN** more than one pending story has all dependencies satisfied
- **THEN** the query returns a deterministic single next story (e.g., lowest `id` in sort order)

### Requirement: Seeding is reproducible and idempotent

The story-graph capability SHALL seed the graph from a `story-seed.cypher` file located in the change root, using `MERGE` semantics so re-running the seed does not duplicate nodes or edges. Seeding SHALL be skipped when a run is already in progress for that change.

#### Scenario: Re-running the seed

- **WHEN** the seed script is executed a second time for the same change with no run in progress
- **THEN** no duplicate `Story` nodes or `DEPENDS_ON` edges are created

#### Scenario: Run already in progress

- **WHEN** a change already has at least one story in `in_progress` or `done` status
- **THEN** the seed is not re-applied and the existing graph state is used
