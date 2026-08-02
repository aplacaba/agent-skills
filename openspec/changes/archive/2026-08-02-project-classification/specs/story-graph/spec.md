## MODIFIED Requirements

### Requirement: Store story graph in Neo4j via MCP

The story-graph capability SHALL store stories and their dependency edges in a Neo4j database, and SHALL access the database exclusively through the Cypher execution tool exposed by the Neo4j MCP server. The graph SHALL model a `Project` node with properties `name`, `repoUrl`, `techStack`, and `type`; a `Change` node with properties `name` and `project`; `Story` nodes with properties `id`, `change`, `project`, `title`, `description`, `acceptanceCriteria`, `status`, and `taskRefs`; `BELONGS_TO` edges from `Project` to `Change`; `HAS_STORY` edges from `Change` to `Story`; and `DEPENDS_ON` edges between stories. `Change` identity is the `{name, project}` pair and `Story` identity is the `{id, change, project}` triple, so same-named changes or stories in different projects SHALL NOT share nodes or execution state.

#### Scenario: Seed stories into graph

- **WHEN** the story graph receives a seeded change
- **THEN** a `Project` node, a `Change` node (with `name` and `project`), and one `Story` node per story (with `id`, `change`, and `project`) are created in the database, with `BELONGS_TO`, `HAS_STORY`, and `DEPENDS_ON` edges connecting them, all scoped to the project

#### Scenario: Status lifecycle

- **WHEN** a story is selected for implementation
- **THEN** its status transitions from `pending` to `in_progress`; when implementation completes it transitions to `done`

#### Scenario: Projects are isolated

- **WHEN** two projects each have a change with the same `name` and a story with the same `id`
- **THEN** each project has its own `Change` and `Story` nodes and no execution state is shared between them

### Requirement: Query readiness from the graph

The story-graph capability SHALL expose a query that returns the next runnable story for a given change within a project: a story whose status is `pending`, whose `change` and `project` match, and where none of its `DEPENDS_ON` targets (within the same change and project) have a status other than `done`.

#### Scenario: Dependencies not satisfied

- **WHEN** a pending story has at least one `DEPENDS_ON` target that is not `done`
- **THEN** that story is not returned as runnable

#### Scenario: All dependencies satisfied

- **WHEN** a pending story has all of its `DEPENDS_ON` targets set to `done`
- **THEN** that story is returned as the next runnable story

#### Scenario: Multiple runnable stories

- **WHEN** more than one pending story has all dependencies satisfied
- **THEN** the query returns a deterministic single next story (e.g., lowest `id` in sort order)

#### Scenario: Query is change- and project-scoped

- **WHEN** the readiness query runs for a change
- **THEN** only stories belonging to that change and project are considered, `DEPENDS_ON` targets are matched within the same change and project, and stories of other changes or projects are never returned

### Requirement: Seeding is reproducible and idempotent

The story-graph capability SHALL seed the graph from a `story-seed.cypher` file located in the change root, using `MERGE` semantics so re-running the seed does not duplicate nodes or edges. Seeding SHALL be skipped when a run is already in progress for that project's change. The seed SHALL link the change to its project via `BELONGS_TO`, and the run-in-progress check SHALL match the change by `{name, project}`.

#### Scenario: Re-running the seed

- **WHEN** the seed script is executed a second time for the same change and project with no run in progress
- **THEN** no duplicate `Project`, `Change`, or `Story` nodes and no duplicate `BELONGS_TO`, `HAS_STORY`, or `DEPENDS_ON` edges are created

#### Scenario: Run already in progress

- **WHEN** a change for a project already has at least one story in `in_progress` or `done` status
- **THEN** the seed is not re-applied and the existing graph state is used

### Requirement: Classify projects in the graph

The story-graph capability SHALL support registering and classifying a project: an agent derives classification facts from the repository (README for `type`, manifest files and directory layout for `techStack`, git remote for `repoUrl`) and writes them to the `Project` node via the Neo4j MCP server's Cypher execution tool, using `MERGE` on `name` with `SET` semantics so re-running refreshes the classification.

The project `name` SHALL be derived canonically from the git remote `origin` URL, accepting HTTPS (`https://host/owner/repo[.git]`), SSH scp-style (`git@host:owner/repo[.git]`), and SSH URL forms (`ssh://[user@]host[:port]/owner/repo[.git]`); the identity SHALL be the lowercase `host/<all path segments except the last>/<last segment>` (preserving subgroups), stripping scheme, userinfo, port, `.git` suffix, trailing slashes, query strings, and fragments. An origin is unparseable when it has no host, no path, fewer than two path segments, or does not match the accepted forms; on absent or unparseable origin the name SHALL fall back to lowercase `local/<repo-directory-name>`.

`type` SHALL be one of `tooling`, `agent`, or `docs`; when not derivable it SHALL be cleared. `techStack` SHALL be a lowercase list of runtime/language names from manifests and directories, deduplicated, sorted alphabetically, with aliases normalized (`js`/`javascript` → `javascript`, `ts`/`typescript` → `typescript`, `py` → `python`). The stored `repoUrl` SHALL NOT contain credentials: userinfo, query strings, and fragments SHALL be stripped; for HTTPS origins the retained form is `https://host/owner/repo` keeping any `.git` suffix as in the origin; SSH origins SHALL be converted to their HTTPS form on known forges (github.com, gitlab.com, bitbucket.org) and otherwise omitted; if no safe form exists, `repoUrl` SHALL be omitted.

#### Scenario: Classify a project

- **WHEN** an agent writes classification for a project via the Cypher tool
- **THEN** a `Project` node with `name` and the derivable properties (`repoUrl`, `techStack`, `type`) exists, with values matching the derivation contracts

#### Scenario: Reclassification refreshes metadata

- **WHEN** classification runs again for the same project with changed facts
- **THEN** the `Project` node's derived properties are overwritten, and properties whose values are no longer derivable are cleared

#### Scenario: No origin available

- **WHEN** a repository has no usable git `origin` remote
- **THEN** the project name falls back to `local/<repo-directory-name>` (lowercased), and the agent is guided to prefer setting `origin`

#### Scenario: Same origin yields the same name

- **WHEN** the same repository is classified from two different clones
- **THEN** the derived project `name` is identical in both
