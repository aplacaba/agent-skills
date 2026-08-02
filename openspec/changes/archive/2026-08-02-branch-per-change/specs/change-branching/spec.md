# change-branching

## Purpose

Defines the worktree-per-change git workflow for this repository: every proposed OpenSpec change gets a dedicated git worktree under `.worktrees/<name>/` on branch `change/<name>`, created at propose time; the main checkout stays on the default branch and is never switched; the archive step ends with a deterministic post-archive handoff — commit remaining work, push the change branch, open a pull request against the default branch, and remove the worktree. Merging happens through the pull request; the branch is kept until it merges.

## Requirements

### Requirement: Worktree per proposed change

Each proposed OpenSpec change SHALL have a dedicated git worktree at `.worktrees/<name>/` (where `<name>` is the kebab-case change name) checked out on branch `change/<name>`, created at propose time before any scaffold or artifact file is written. All work for the change (proposal artifacts, implementation commits, spec syncs) SHALL happen inside that worktree. The main checkout SHALL stay on the default branch and SHALL never be switched. The default branch SHALL receive proposed-change commits only when the change's pull request merges; bug fixes, which require no proposal, SHALL remain exempt and may commit directly on the default branch in the main checkout.

#### Scenario: Worktree created at propose

- **WHEN** the propose phase starts for a change named `branch-per-change`
- **THEN** a git worktree at `.worktrees/branch-per-change/` on branch `change/branch-per-change` exists before the change scaffold is created, and all subsequent artifact files land inside it

#### Scenario: Main checkout never switched

- **WHEN** any phase of a change's lifecycle runs
- **THEN** the main checkout remains on the default branch and no phase switches it

#### Scenario: Default branch receives commits via pull requests

- **WHEN** a change's pull request merges
- **THEN** its commits become part of the default branch; before merging, no change commit is on the default branch

#### Scenario: Bug fixes bypass the worktree

- **WHEN** a bug fix without an OpenSpec proposal is committed
- **THEN** it commits directly on the default branch in the main checkout with no worktree or change branch

### Requirement: Convention detection

The worktree-per-change convention SHALL be considered active in a repository only when it is a git repository AND its root `AGENTS.md` contains the exact heading `## Branching strategy` with the documented `change/<name>` worktree convention. When the convention is inactive, skills SHALL warn once and skip all git steps without failing the workflow. A missing change worktree in a convention-active repository SHALL be treated as a workflow violation that stops apply/sync/archive — never as evidence that the convention is inactive.

#### Scenario: Convention active

- **WHEN** the working directory is a git repository and its root `AGENTS.md` contains the `## Branching strategy` heading
- **THEN** all git steps (worktree creation, verification, handoff) run as specified

#### Scenario: Convention inactive

- **WHEN** the working directory is not a git repository, or its root `AGENTS.md` lacks the `## Branching strategy` heading (e.g. a standalone OpenSpec store)
- **THEN** the skills warn once and skip all git steps, and the workflow completes without them

#### Scenario: Missing worktree is a violation

- **WHEN** the apply, sync, or archive phase runs in a convention-active repository and the change worktree does not exist
- **THEN** the phase stops and reports without creating the worktree late and without falling back to the main checkout

### Requirement: Ensure-worktree procedure

All phases SHALL use a shared ensure-worktree procedure: the change worktree exists at `.worktrees/<name>/` and is verified with `git worktree list --porcelain` to be a registered git worktree on exactly branch `change/<name>`; a stale directory or a worktree on another branch SHALL stop the flow. The propose phase SHALL create the worktree — after verifying the main checkout is on the resolved default branch — with `git worktree add -b change/<name>` whose start point is `FETCH_HEAD` after `git fetch <remote> <base>`, or by re-checking out an existing `change/<name>` on re-propose. Apply, sync, and archive phases SHALL NOT create the worktree; they stop and report when it is missing.

#### Scenario: Worktree verified before use

- **WHEN** a phase is about to operate on a change
- **THEN** it verifies via `git worktree list --porcelain` that `.worktrees/<name>/` is a registered worktree on exactly `change/<name>`, and stops on a stale directory or wrong branch

#### Scenario: Propose creates the worktree from the remote default branch

- **WHEN** the propose phase runs in a convention-active repository and the worktree does not exist
- **THEN** it verifies the main checkout is on the resolved default branch, fetches the remote default branch, creates `.worktrees/<name>/` on `change/<name>` from `FETCH_HEAD`, and the main checkout is never switched

### Requirement: Phase enforcement

The propose phase SHALL run the shared preflight (no foreign in-progress git operation in the main checkout or the change worktree) before creating the worktree and again immediately before its final commit, and SHALL commit the completed artifacts with `chore(openspec): propose change <name>`, staging only files under `openspec/changes/<name>/`. The apply phase SHALL run the shared preflight before starting and immediately before every commit, and SHALL operate inside the worktree. The sync phase SHALL run the shared preflight before syncing and immediately before its commit, and SHALL commit the synced delta specs with `chore(openspec): sync <name> specs`. The archive phase SHALL run the shared preflight and ensure-worktree before moving the change directory, and SHALL record the exact archive destination together with the remote and branch pins in a durable state file before the first remote-touching operation.

#### Scenario: Propose commits artifacts in the worktree

- **WHEN** all proposal artifacts for a change are complete and verified
- **THEN** only the files under `openspec/changes/<name>/` are staged and committed in the worktree with the message `chore(openspec): propose change <name>`

#### Scenario: Sync commits on the branch

- **WHEN** the sync phase updates main specs from a change's delta specs
- **THEN** the delta spec changes are committed in the worktree with `chore(openspec): sync <name> specs`

#### Scenario: Archive verifies before moving and records pins

- **WHEN** the archive phase is about to move the change directory into the archive
- **THEN** the preflight and ensure-worktree ran first, and the archive destination plus remote and branch pins were persisted in a durable state file before any remote-touching operation

### Requirement: Remote and default-branch selection

Every remote-touching step SHALL use one deterministic selection. The remote is selected as the first remote whose HEAD symbolic ref resolves (`git symbolic-ref refs/remotes/<r>/HEAD`); if none resolves, `origin` when it exists, else the only remote, else the flow SHALL stop and ask the user. The default branch SHALL be resolved freshly (`git ls-remote --symref <remote> HEAD`, parsing the `ref: refs/heads/<branch> HEAD` record) for both the initial archive and every resume; if unresolvable, fall back to the first existing local `master` then `main`, else stop and ask. With no remote at all, the flow SHALL use the explicit no-remote state and the local `master`/`main` fallback. Remote endpoints SHALL be reduced to canonical repository identities (`<lowercase-host>[:port]/<path-segments>`, scheme-agnostic, userinfo/query/fragment stripped, SCP-style parsed after the last `@`); unparseable endpoints SHALL be pinned by a non-secret SHA-256 fingerprint of the endpoint string. The archive SHALL persist these pins (remote name, effective push endpoint list via `git remote get-url --push --all`, base repository identity, resolved base branch) in the durable state file; a resume SHALL revalidate them (including a fresh default-branch resolution) before any remote-touching step, offering a user-authorized repin on mismatch.

#### Scenario: Default branch resolved freshly

- **WHEN** the archive phase starts or resumes
- **THEN** the base branch is resolved from `git ls-remote --symref <remote> HEAD` and the local stale `refs/remotes/<remote>/HEAD` is never used as the source of truth

#### Scenario: Pins revalidated on resume

- **WHEN** a post-ops resume runs with an unchanged remote configuration
- **THEN** the remote name, endpoint identities, base identity, and freshly resolved base branch match the recorded pins and the handoff continues against the same repositories

#### Scenario: User-authorized repin

- **WHEN** a resume detects a pin mismatch and the user authorizes a repin
- **THEN** the current configuration is recorded as the new pins in the durable state file before any remote operation resumes

### Requirement: Post-archive git handoff

After the change directory has been moved into the archive, the archive phase SHALL run the resumable post-ops in this order:

1. preflight — stop if any foreign git operation (merge, rebase, cherry-pick, revert) is in progress in the main checkout or the change worktree (when it exists);
2. locate state — if the worktree exists, revalidate it and continue; if it is missing, verify remote state against every pinned endpoint and the pull-request status (an open or merged matching pull request completes the handoff, a merged pull request waiving branch presence; a missing or stale branch on any endpoint without an open/merged pull request stops as an anomaly);
3. commit remaining work in the worktree with `chore(openspec): archive change <name>`, staging only attributable paths (both sides of the archive move as a rename, the `openspec/specs/<capability>/spec.md` files matching the change's delta capabilities, and the concrete file paths named in the change's `tasks.md` — validated and skipped when absent); any other dirty path SHALL prompt with approve/decline/exclude continuations;
4. push `change/<name>` to every effective push endpoint and verify the branch at the pushed tip on every endpoint (failure stops before the pull-request step and removal; no remote skips push, pull request, and removal with a warning);
5. open the pull request via the forge's pull-request tool against the base and head repositories derived from the pins, deduplicating on head branch in the head repository and base branch in the base repository — an open matching pull request is reported without creating a duplicate, a merged one is reported, a closed-unmerged one asks the user to reopen or recreate (declining both stops and retains the worktree), and an unreachable or incompatible forge (cross-host head/base, multiple distinct push identities) stops with the worktree retained or hands off manually as specified;
6. remove the worktree (`git worktree remove`, which refuses on a dirty tree) as the final cleanup — non-blocking on refusal: the handoff is reported complete and the worktree is left for the user;
7. report the pull request URL and status, and remind the user that merging happens through the pull request.

A failed post-ops SHALL be resumable: re-invoking the archive flow with the recorded archive destination SHALL skip the pre-move checks, enter post-ops directly, and revalidate the durable pins before remote-touching steps.

#### Scenario: Successful handoff

- **WHEN** the post-ops runs on a change worktree with a clean tree and a reachable forge
- **THEN** remaining work is committed, the branch is pushed to every endpoint, the pull request is opened (or an existing one reported), the worktree is removed, and the pull request URL is reported

#### Scenario: Push failure preserves state

- **WHEN** the push fails on any endpoint
- **THEN** the post-ops stops before the pull-request step and removal, the worktree and state file remain, and a re-run retries the push

#### Scenario: No remote skips the remote steps

- **WHEN** no remote is configured
- **THEN** the push, pull-request, and removal steps are skipped with a warning, the local state is reported, and the worktree is kept for the user

#### Scenario: Out-of-scope dirty files prompt

- **WHEN** the post-ops commit step finds dirty paths outside the attributable set
- **THEN** it prompts with approve (stage and mark user-approved), decline (stop the post-ops), or exclude (user cleans up manually and re-runs)

#### Scenario: Closed-unmerged pull request asks the user

- **WHEN** the post-ops finds a closed-unmerged matching pull request
- **THEN** it asks the user to reopen or recreate it; if the user declines both options, the post-ops stops and retains the worktree

#### Scenario: Worktree removal is non-blocking

- **WHEN** the worktree is dirty at removal time
- **THEN** the post-ops warns, reports the handoff as complete, and leaves the worktree for the user or a later re-run

#### Scenario: Resume after worktree removal

- **WHEN** the post-ops is re-run after the worktree was removed
- **THEN** it verifies remote state and pull-request status against every pinned endpoint, reports the pull request, or stops as an anomaly — without recreating the worktree

### Requirement: Skill neutrality

All git steps in canonical skills SHALL use harness-neutral wording ("the git tool", "the default branch", "the forge's pull-request tool") and SHALL be conditional on the convention detection, so that no git step fails the workflow in repositories or stores where the convention is inactive. This change SHALL add a pull-request tool mapping row to `docs/harness-mapping.md` (forge → pull-request tool names). Raw endpoint strings SHALL never appear in skill output, logs, or reports — only canonical identities or fingerprints, so credentials embedded in URLs are never exposed.

#### Scenario: Foreign repository no-op

- **WHEN** a canonical skill runs in a repository without the `## Branching strategy` section
- **THEN** the skill warns once, skips all git steps, and the rest of the workflow completes normally

#### Scenario: Credentials never exposed

- **WHEN** a remote endpoint URL contains userinfo or tokens
- **THEN** only the canonical identity or fingerprint of the endpoint appears in any pin, report, log, or history — never the raw URL
