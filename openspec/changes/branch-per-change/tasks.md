## 1. Bootstrap this change

- [ ] 1.1 Create the change worktree from the main checkout: `git worktree add -b change/branch-per-change .worktrees/branch-per-change master`; move the proposal artifacts from the main checkout into `.worktrees/branch-per-change/openspec/changes/branch-per-change/`; stage only that directory and commit with `chore(openspec): propose change branch-per-change`; leave unrelated dirty files (e.g. the modified archived story-seed file) untouched in the main checkout

## 2. Documentation and repo plumbing

- [ ] 2.1 Add a `## Branching strategy` section to `AGENTS.md`: each proposed change works in a `.worktrees/<name>/` worktree on branch `change/<name>` created at propose time; the main checkout stays on the default branch; the default branch receives proposed-change commits only from merged change pull requests (bug fixes exempt, committed directly on the default branch); the archive step commits, pushes the change branch, opens a pull request, and removes the worktree; the branch is kept until the pull request merges
- [ ] 2.2 Update `README.md` with a worktree-per-change paragraph covering worktree creation at propose time, the main-checkout policy, and the post-archive git steps
- [ ] 2.3 Update the derived-fact lines in `AGENTS.md` (repository layout / workflow facts) and verify them against the repo state
- [ ] 2.4 Add `.worktrees/` to `.gitignore`
- [ ] 2.5 Add a "pull-request tool" mapping row to `docs/harness-mapping.md` (forge → pull-request tool names) for consistent PR open/detect/report across harnesses

## 3. Propose skill and shared git helpers

- [ ] 3.1 Define the shared convention detection (git repo + `## Branching strategy` heading in root `AGENTS.md`) and the shared preflight (stop on any foreign in-progress merge/rebase/cherry-pick/revert in the main checkout and in the change worktree, when it exists); described once, used by all four skills
- [ ] 3.2 Define the shared ensure-worktree procedure: verify the worktree exists and is a registered git worktree on exactly `change/<name>` (`git worktree list --porcelain`); stale directory or wrong branch stops and reports
- [ ] 3.3 In `skills/openspec-propose/SKILL.md`, before `openspec new change`: run preflight, verify the main checkout is on the resolved default branch (stop and ask otherwise), then create `.worktrees/<name>/` with `git worktree add -b change/<name>` (start point = `FETCH_HEAD` after `git fetch <remote> <base>`, or re-checkout of an existing `change/<name>` on re-propose); warn and skip all git steps when the convention is inactive
- [ ] 3.4 In `skills/openspec-propose/SKILL.md`, after all artifacts are done and reviewed: run the shared preflight again, stage only `openspec/changes/<name>/`, and commit with `chore(openspec): propose change <name>` (all commits include a body explaining why)

## 4. Apply skill

- [ ] 4.1 In `skills/openspec-apply-change/SKILL.md`, before starting: run the shared preflight and ensure-worktree (stop and report when the worktree is missing in a convention-active repository; warn and skip when the convention is inactive); all change work happens inside the worktree, and the shared preflight runs immediately before every commit

## 5. Sync skill

- [ ] 5.1 In `skills/openspec-sync-specs/SKILL.md`, before syncing: run the shared preflight and ensure-worktree (same rules as 4.1); all change work happens inside the worktree
- [ ] 5.2 In `skills/openspec-sync-specs/SKILL.md`, after the sync: run the shared preflight again, then commit the delta spec changes with `chore(openspec): sync <name> specs`

## 6. Archive skill

- [ ] 6.1 In `skills/openspec-archive-change/SKILL.md`, before the archive move: run the shared preflight and ensure-worktree; record the exact archive destination and the D5 pins (remote name or explicit no-remote state, effective push endpoint list, base repository identity, resolved base branch) in a durable state file under the git common directory (`git rev-parse --git-common-dir`), written atomically before the first remote-touching operation
- [ ] 6.2 In `skills/openspec-archive-change/SKILL.md`, add the post-ops entry with preflight and state location: worktree exists → revalidate it (ensure-worktree), then commit/push/PR/remove; worktree missing → verify remote state against every pinned endpoint and the pull-request status, then finish, open the PR, or stop as an anomaly per the design
- [ ] 6.3 In `skills/openspec-archive-change/SKILL.md`, add the attributable-path commit inside the worktree: both sides of the archive move staged as a rename (conditional on the source still being tracked), delta-spec files and task-listed paths validated with `git ls-files`/existence checks (absent paths skipped and reported), out-of-scope dirty paths prompt with approve/decline/exclude, commit with `chore(openspec): archive change <name>`
- [ ] 6.4 In `skills/openspec-archive-change/SKILL.md`, add the push step: push `change/<name>` to every effective push endpoint and verify the branch at the pushed tip on every endpoint; failure stops before the PR step and removal; no remote → skip push/PR/removal with a warning and report the local state
- [ ] 6.5 In `skills/openspec-archive-change/SKILL.md`, add the pull-request step via the forge's pull-request tool: dedup on head branch in the head repository + base branch in the base repository; open → report and continue; merged → report and continue; closed-unmerged → ask user to reopen or recreate (declining both stops and retains the worktree); forge unreachable → warn and hand off manually; multiple distinct push identities or cross-host head/base → stop and retain the worktree pending configuration resolution
- [ ] 6.6 In `skills/openspec-archive-change/SKILL.md`, add the final worktree removal (`git worktree remove`, refuses on dirty tree): non-blocking — on refusal, warn, report the handoff as complete, and leave the worktree for the user
- [ ] 6.7 In `skills/openspec-archive-change/SKILL.md`, add the resume dispatch: invoked with an already-moved archive destination → skip the pre-move checks and enter post-ops directly; revalidate the durable pins (remote name, endpoints, base identity, fresh default-branch resolution) with a user-authorized repin option on mismatch; multiple archive directories for one name without an explicit destination → stop and ask the user

## 7. Verification

- [ ] 7.1 Verify the documentation facts (2.1–2.3) against the repository per the derived-fact rule
- [ ] 7.2 Check each skill's git steps against the `change-branching` spec scenarios (convention detection, ensure-worktree, preflight, post-ops outcome contract) and confirm the babashka test suites still pass: `bb scripts/test_story_driver.clj` and `bb scripts/test_config_merge.clj`
- [ ] 7.3 Confirm every added git instruction in the four skills uses harness-neutral wording (generic tool names, per `AGENTS.md`) and never names opencode-specific tools
- [ ] 7.4 Confirm no raw endpoint strings (with potential credentials) appear in any skill output, log, or report path — only canonical identities or fingerprints

## 8. Archive this change

- [ ] 8.1 Run the archive flow for `branch-per-change`; the checkpoint model is: the apply loop must NOT tick this task in advance — the archive flow ticks it in the moved `tasks.md` as part of its attributable archive commit, and completion is defined by the flow's success report (branch pushed, pull request opened or resolved, worktree removed); a failed flow leaves the worktree and state file in place and is resumed via the archive resume dispatch
