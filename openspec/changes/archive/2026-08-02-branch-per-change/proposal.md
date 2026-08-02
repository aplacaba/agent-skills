## Why

Every change is currently implemented directly on `master`, so commits from different changes interleave and there is no isolated point for per-change review or rollback. A worktree-per-change strategy gives each OpenSpec change its own git worktree on a dedicated branch, pushed to the remote with a pull request opened against the default branch as part of the archive step; merging happens through that pull request, so `master` receives proposed-change commits only when their pull request merges.

## What Changes

- Each OpenSpec change gets a dedicated git worktree under `.worktrees/<name>/` on a branch named `change/<name>`, created when the change is proposed. All work for the change (proposal artifacts, implementation commits, spec syncs) happens inside that worktree; the main checkout stays on the default branch and is never switched. For proposed changes, `master` receives commits only when the change's pull request merges.
- The archive step gains explicit post-archive git steps: commit remaining work, push the change branch to the remote, open a pull request against the default branch, then remove the worktree — with fail-safe behavior (see design). Merging is deliberately NOT part of the archive flow: it happens through the pull request's review process. The change branch (local and remote) is kept until the pull request merges; only the worktree is removed at archive time.
- The strategy is documented in `AGENTS.md` and `README.md` and encoded in the `openspec-propose`, `openspec-apply-change`, `openspec-sync-specs`, and `openspec-archive-change` skills so every phase of the workflow follows it automatically.
- The existing `repo-documentation` requirement "Existing files unchanged" (which forbids modifying any existing file) is removed and replaced, since this change intentionally modifies `README.md` and `AGENTS.md`.
- The repository's derived-fact lines in `README.md` and `AGENTS.md` are updated in the same change.

## Capabilities

### New Capabilities
- `change-branching`: the worktree-per-change workflow — worktree location (`.worktrees/<name>/`), branch naming (`change/<name>`), worktree creation at propose time (before any artifact is written), the commit policy (all change work in the worktree, nothing on `master` until the pull request merges), the bug-fix exception, and the fail-safe push/pull-request/worktree-removal steps performed at archive time.

### Modified Capabilities
- `repo-documentation`: the "Existing files unchanged" requirement is removed and replaced by requirements that `README.md` and `AGENTS.md` state the `change/<name>` worktree convention, the rule that proposed changes work in their change worktree and `master` only receives commits from merged change pull requests (bug fixes are exempt), and the post-archive git steps.

## Impact

- `AGENTS.md`, `README.md`: new branching-strategy documentation section; the derived-fact lines are updated.
- `skills/openspec-propose/SKILL.md`: create the `.worktrees/<name>/` worktree (with branch `change/<name>`) before the change scaffold and its artifacts are created, so every artifact of the change is born in the worktree.
- `skills/openspec-apply-change/SKILL.md` and `skills/openspec-sync-specs/SKILL.md`: ensure the change worktree exists and operate all change work inside it; the main checkout is never switched.
- `skills/openspec-archive-change/SKILL.md`: add the post-archive git steps (commit, push the change branch, open a pull request against the default branch, remove the worktree), guarded for environments without a remote or without a pull-request-capable forge.
- `.gitignore`: add `.worktrees/` so change worktrees stay untracked.
- Policy scope: the convention is defined for this repository via `AGENTS.md`. The skills implement the git steps generically with harness-neutral wording and safe guards, so they remain valid in other repositories and standalone OpenSpec stores (they no-op or warn when the convention does not apply, e.g. the repository has no `## Branching strategy` section). A missing change worktree in a convention-active repository is a workflow violation that stops apply/sync/archive — it is never treated as evidence that the convention is inactive.
- Bug fixes (which require no proposal) are exempt: they may continue to be committed directly on `master`.
- Git history and workflow of the repository: all future proposed changes follow the branch-per-change model.
- No impact on the Neo4j story graph or the babashka scripts.

## Bootstrap

This change was proposed on `master` under the old workflow. Before implementation begins, its artifacts are moved into a `.worktrees/branch-per-change/` worktree on branch `change/branch-per-change` so that this change itself follows the new policy from the start of apply.
