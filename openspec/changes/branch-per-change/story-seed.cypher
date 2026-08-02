// Story graph seed for change
// change: "branch-per-change"
// project: "github.com/aplacaba/agent-skills"
// Idempotent: each statement uses MERGE; safe to re-run.
MERGE (p:Project {name: "github.com/aplacaba/agent-skills"});
MERGE (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"});
MATCH (p:Project {name: "github.com/aplacaba/agent-skills"}),
      (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (p)-[:BELONGS_TO]->(c);

MERGE (s:Story {id: "bootstrap-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Bootstrap this change into its worktree", s.description = "WHAT: Create the change worktree for branch-per-change from the main checkout, move the proposal artifacts into it, and commit them there.\nFILES:\n  - create: .worktrees/branch-per-change/ (git worktree on branch change/branch-per-change, created from the main checkout)\n  - modify: openspec/changes/branch-per-change/ (move the existing proposal artifacts from the main checkout into the worktree)\nEDGE CASES:\n  - The main checkout has an unrelated dirty file (openspec/changes/archive/2026-08-02-project-classification/story-seed.cypher): it must stay untouched and uncommitted in the main checkout.\n  - The worktree or branch already exists: stop and report instead of recreating.\nCONTEXT: Design \"Migration Plan\" step 1 (bootstrap). Subsequent stories operate inside this worktree; paths in later stories are relative to the worktree root.", s.acceptanceCriteria = [".worktrees/branch-per-change/ exists and `git worktree list --porcelain` reports it on branch change/branch-per-change", "openspec/changes/branch-per-change/ contains proposal.md, design.md, tasks.md, specs/ in the worktree, committed there with subject `chore(openspec): propose change branch-per-change` staging only that directory", "The main checkout is still on master and the modified archived story-seed.cypher file is still uncommitted/untouched"], s.taskRefs = ["1.1 Create the change worktree from the main checkout: `git worktree add -b change/branch-per-change .worktrees/branch-per-change master`; move the proposal artifacts from the main checkout into `.worktrees/branch-per-change/openspec/changes/branch-per-change/`; stage only that directory and commit with `chore(openspec): propose change branch-per-change`; leave unrelated dirty files (e.g. the modified archived story-seed file) untouched in the main checkout"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "bootstrap-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "docs-agents-branching", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Document the branching strategy in AGENTS.md", s.description = "WHAT: Add the `## Branching strategy` section to AGENTS.md and update its derived-fact lines.\nFILES:\n  - modify: AGENTS.md\nEDGE CASES:\n  - AGENTS.md already contains a `## Branching strategy` section: replace its content, do not duplicate.\n  - The derived-fact line update must keep every existing fact (prerequisites, script paths, layout) consistent with the repo.\nCONTEXT: Spec repo-documentation \"Root AGENTS.md with working conventions\" + \"Branching strategy stated\" scenario; design D1/D2. Operate inside the worktree `.worktrees/branch-per-change/`.", s.acceptanceCriteria = ["AGENTS.md contains exactly one `## Branching strategy` heading stating the `.worktrees/<name>/` worktree on `change/<name>`, main checkout stays on the default branch, default branch receives proposed-change commits only from merged change pull requests (bug fixes exempt), and the archive step commits, pushes, opens a pull request, and removes the worktree", "The derived-fact lines (repository layout / workflow facts) in AGENTS.md are updated and consistent with the repo state", "No other AGENTS.md facts (prerequisites, script/test invocations, MCP note, conventional commits) were removed or changed"], s.taskRefs = ["2.1 Add a `## Branching strategy` section to `AGENTS.md`: each proposed change works in a `.worktrees/<name>/` worktree on branch `change/<name>` created at propose time; the main checkout stays on the default branch; the default branch receives proposed-change commits only from merged change pull requests (bug fixes exempt, committed directly on the default branch); the archive step commits, pushes the change branch, opens a pull request, and removes the worktree; the branch is kept until the pull request merges", "2.3 Update the derived-fact lines in `AGENTS.md` (repository layout / workflow facts) and verify them against the repo state"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "docs-agents-branching", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "docs-readme-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Document the worktree workflow in README.md", s.description = "WHAT: Add a worktree-per-change paragraph to README.md.\nFILES:\n  - modify: README.md\nEDGE CASES:\n  - README.md already mentions the old single-branch flow: replace it with the worktree description; do not duplicate.\n  - Keep existing README facts (prerequisites, quick start, layout) intact.\nCONTEXT: Spec repo-documentation \"Root README for humans\" + \"Newcomer reads the README\" scenario. Operate inside the worktree `.worktrees/branch-per-change/`.", s.acceptanceCriteria = ["README.md contains a worktree-per-change paragraph covering worktree creation at propose time (`.worktrees/<name>/` on `change/<name>`), the main-checkout policy, and the post-archive git steps (commit, push, open pull request, remove worktree)", "All pre-existing README facts (prerequisite list, script paths, canonical layout, links to docs/) remain intact"], s.taskRefs = ["2.2 Update `README.md` with a worktree-per-change paragraph covering worktree creation at propose time, the main-checkout policy, and the post-archive git steps"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "docs-readme-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "repo-plumbing", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add repo plumbing (.gitignore and harness mapping)", s.description = "WHAT: Add `.worktrees/` to .gitignore and add a pull-request tool mapping row to docs/harness-mapping.md.\nFILES:\n  - modify: .gitignore\n  - modify: docs/harness-mapping.md\nEDGE CASES:\n  - .gitignore already lists `.worktrees/`: no duplicate line.\n  - harness-mapping.md table format must be preserved; the new row names the forge's pull-request tool generically (e.g. gh, glab, or the harness-specific name) per the existing mapping style.\nCONTEXT: Design D8 (skill neutrality, PR tool mapping row). Operate inside the worktree `.worktrees/branch-per-change/`.", s.acceptanceCriteria = [".gitignore contains `.worktrees/` exactly once", "docs/harness-mapping.md has a row mapping forges to their pull-request tool names, consistent with the existing table structure"], s.taskRefs = ["2.4 Add `.worktrees/` to `.gitignore`", "2.5 Add a \"pull-request tool\" mapping row to `docs/harness-mapping.md` (forge → pull-request tool names) for consistent PR open/detect/report across harnesses"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "repo-plumbing", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Define shared convention detection, preflight, and ensure-worktree helpers", s.description = "WHAT: In skills/openspec-propose/SKILL.md, define the shared git helpers used by all four skills: convention detection, preflight, and ensure-worktree.\nFILES:\n  - modify: skills/openspec-propose/SKILL.md\nEDGE CASES:\n  - The wording must be harness-neutral (no opencode-specific tool names).\n  - Preflight must check the main checkout always and the change worktree only when it exists.\n  - Ensure-worktree must validate with `git worktree list --porcelain` that the path is a registered worktree on exactly `change/<name>`; a stale directory or wrong branch stops and reports.\nCONTEXT: Design D2 (convention detection), D6.0 (preflight), D3 (ensure-worktree). These are the shared helper definitions; later stories reference them.", s.acceptanceCriteria = ["skills/openspec-propose/SKILL.md defines convention detection (git repo + exact `## Branching strategy` heading in root AGENTS.md) and the shared preflight (stop on foreign in-progress merge/rebase/cherry-pick/revert in the main checkout and in the change worktree when it exists), described once for reuse by all four skills", "skills/openspec-propose/SKILL.md defines the ensure-worktree procedure validating via `git worktree list --porcelain` that `.worktrees/<name>/` is a registered worktree on exactly `change/<name>`, stopping on stale directory or wrong branch", "All helper wording is harness-neutral (no opencode-specific tool names)"], s.taskRefs = ["3.1 Define the shared convention detection (git repo + `## Branching strategy` heading in root `AGENTS.md`) and the shared preflight (stop on any foreign in-progress merge/rebase/cherry-pick/revert in the main checkout and in the change worktree, when it exists); described once, used by all four skills", "3.2 Define the shared ensure-worktree procedure: verify the worktree exists and is a registered git worktree on exactly `change/<name>` (`git worktree list --porcelain`); stale directory or wrong branch stops and reports"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "propose-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add worktree creation and propose commit to the propose skill", s.description = "WHAT: In skills/openspec-propose/SKILL.md, add the ensure-worktree creation step before `openspec new change` and the final propose commit.\nFILES:\n  - modify: skills/openspec-propose/SKILL.md\nEDGE CASES:\n  - Main checkout not on the resolved default branch: stop and ask before creating the worktree.\n  - `change/<name>` branch already exists (re-propose): use `git worktree add <path> change/<name>` instead of creating a new branch.\n  - Convention inactive: warn and skip all git steps.\n  - Preflight runs again immediately before the final commit.\nCONTEXT: Design D3 step 3 (creation from FETCH_HEAD after `git fetch <remote> <base>`) and D4 propose bullet.", s.acceptanceCriteria = ["The propose skill runs the shared preflight, verifies the main checkout is on the resolved default branch (stop and ask otherwise), and creates `.worktrees/<name>/` with `git worktree add -b change/<name>` from FETCH_HEAD after `git fetch <remote> <base>` (or re-checks out an existing `change/<name>` on re-propose) before `openspec new change`", "After artifacts are done and reviewed, the skill runs the shared preflight again, stages only `openspec/changes/<name>/`, and commits with `chore(openspec): propose change <name>`", "When the convention is inactive, the skill warns and skips all git steps"], s.taskRefs = ["3.3 In `skills/openspec-propose/SKILL.md`, before `openspec new change`: run preflight, verify the main checkout is on the resolved default branch (stop and ask otherwise), then create `.worktrees/<name>/` with `git worktree add -b change/<name>` (start point = `FETCH_HEAD` after `git fetch <remote> <base>`, or re-checkout of an existing `change/<name>` on re-propose); warn and skip all git steps when the convention is inactive", "3.4 In `skills/openspec-propose/SKILL.md`, after all artifacts are done and reviewed: run the shared preflight again, stage only `openspec/changes/<name>/`, and commit with `chore(openspec): propose change <name>` (all commits include a body explaining why)"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "propose-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "apply-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add worktree enforcement to the apply skill", s.description = "WHAT: In skills/openspec-apply-change/SKILL.md, add preflight and ensure-worktree before starting, and preflight before every commit; all change work happens inside the worktree.\nFILES:\n  - modify: skills/openspec-apply-change/SKILL.md\nEDGE CASES:\n  - Worktree missing in a convention-active repository: stop and report (never create late, never fall back to the main checkout).\n  - Convention inactive: warn and skip.\nCONTEXT: Design D3/D4 apply bullet; spec \"Phase enforcement\".", s.acceptanceCriteria = ["The apply skill runs the shared preflight and ensure-worktree before starting, and the shared preflight immediately before every commit", "All change work is performed inside the worktree `.worktrees/<name>/`; a missing worktree in a convention-active repository stops the skill with a report", "When the convention is inactive, the skill warns and skips the git steps"], s.taskRefs = ["4.1 In `skills/openspec-apply-change/SKILL.md`, before starting: run the shared preflight and ensure-worktree (stop and report when the worktree is missing in a convention-active repository; warn and skip when the convention is inactive); all change work happens inside the worktree, and the shared preflight runs immediately before every commit"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "apply-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "sync-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add worktree enforcement and sync commit to the sync skill", s.description = "WHAT: In skills/openspec-sync-specs/SKILL.md, add preflight and ensure-worktree before syncing, and a preflight + commit of the delta specs afterwards.\nFILES:\n  - modify: skills/openspec-sync-specs/SKILL.md\nEDGE CASES:\n  - Worktree missing in a convention-active repository: stop and report.\n  - Convention inactive: warn and skip.\nCONTEXT: Design D4 sync bullet; spec \"Phase enforcement\".", s.acceptanceCriteria = ["The sync skill runs the shared preflight and ensure-worktree before syncing (same rules as apply)", "After the sync, the skill runs the shared preflight again and commits the delta spec changes with `chore(openspec): sync <name> specs`"], s.taskRefs = ["5.1 In `skills/openspec-sync-specs/SKILL.md`, before syncing: run the shared preflight and ensure-worktree (same rules as 4.1); all change work happens inside the worktree", "5.2 In `skills/openspec-sync-specs/SKILL.md`, after the sync: run the shared preflight again, then commit the delta spec changes with `chore(openspec): sync <name> specs`"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "sync-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "archive-pre-move-state", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add archive pre-move checks and durable pin state", s.description = "WHAT: In skills/openspec-archive-change/SKILL.md, add the pre-move preflight + ensure-worktree, and the durable state file recording the archive destination and D5 pins.\nFILES:\n  - modify: skills/openspec-archive-change/SKILL.md\nEDGE CASES:\n  - State file location uses `git rev-parse --git-common-dir` (not a hardcoded .git directory); write atomically (temp + rename) before the first remote-touching operation.\n  - Pins include: remote name or explicit no-remote state, effective push endpoint list, base repository identity, resolved base branch.\n  - No raw endpoint strings in the state file or reports — only canonical identities or SHA-256 fingerprints.\nCONTEXT: Design D4 archive bullet, D5 pins + durable state, D6.0/D6.1.", s.acceptanceCriteria = ["The archive skill runs the shared preflight and ensure-worktree before the archive move", "The skill records the archive destination and the D5 pins in a durable state file under `git rev-parse --git-common-dir`, written atomically before the first remote-touching operation, containing no raw endpoint strings (only canonical identities or fingerprints)"], s.taskRefs = ["6.1 In `skills/openspec-archive-change/SKILL.md`, before the archive move: run the shared preflight and ensure-worktree; record the exact archive destination and the D5 pins (remote name or explicit no-remote state, effective push endpoint list, base repository identity, resolved base branch) in a durable state file under the git common directory (`git rev-parse --git-common-dir`), written atomically before the first remote-touching operation", "6.2 In `skills/openspec-archive-change/SKILL.md`, add the post-ops entry with preflight and state location: worktree exists → revalidate it (ensure-worktree), then commit/push/PR/remove; worktree missing → verify remote state against every pinned endpoint and the pull-request status, then finish, open the PR, or stop as an anomaly per the design"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "archive-pre-move-state", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "archive-commit-push", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add the archive commit and push steps", s.description = "WHAT: In skills/openspec-archive-change/SKILL.md, add the attributable-path commit inside the worktree and the push with per-endpoint tip verification.\nFILES:\n  - modify: skills/openspec-archive-change/SKILL.md\nEDGE CASES:\n  - Both sides of the archive move staged as a rename, conditional on the source still being tracked (`git ls-files` guard); absent task-listed paths skipped and reported, never passed to `git add`.\n  - Out-of-scope dirty paths prompt with approve / decline / exclude; decline stops the post-ops.\n  - Push failure or tip-verification failure on any endpoint stops before the PR step and removal.\n  - No remote: skip push/PR/removal with a warning and report the local state.\nCONTEXT: Design D6.2 (attribution) and D6.3 (push + verification).", s.acceptanceCriteria = ["The skill stages only attributable paths (both sides of the archive move as a rename, delta-spec files, validated task-listed paths), prompts with approve/decline/exclude on any other dirty path, and commits with `chore(openspec): archive change <name>`", "The skill pushes `change/<name>` to every effective push endpoint and verifies the branch at the pushed tip on every endpoint, stopping before the PR step and removal on any failure", "With no remote configured, push, PR, and removal are skipped with a warning and the local state is reported"], s.taskRefs = ["6.3 In `skills/openspec-archive-change/SKILL.md`, add the attributable-path commit inside the worktree: both sides of the archive move staged as a rename (conditional on the source still being tracked), delta-spec files and task-listed paths validated with `git ls-files`/existence checks (absent paths skipped and reported), out-of-scope dirty paths prompt with approve/decline/exclude, commit with `chore(openspec): archive change <name>`", "6.4 In `skills/openspec-archive-change/SKILL.md`, add the push step: push `change/<name>` to every effective push endpoint and verify the branch at the pushed tip on every endpoint; failure stops before the PR step and removal; no remote → skip push/PR/removal with a warning and report the local state"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "archive-commit-push", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "archive-pr-step", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add the pull-request step to the archive skill", s.description = "WHAT: In skills/openspec-archive-change/SKILL.md, add the pull-request open/detect/report step via the forge's pull-request tool.\nFILES:\n  - modify: skills/openspec-archive-change/SKILL.md\nEDGE CASES:\n  - Dedup tuple: head branch in the head repository + base branch in the base repository; open matching PR → report and continue (no duplicate).\n  - Merged matching PR → report as merged and continue; branch presence waived for that endpoint.\n  - Closed-unmerged matching PR → ask the user to reopen or recreate; declining both stops the post-ops and retains the worktree.\n  - Forge unreachable or not PR-capable → warn, hand off manually, continue to removal.\n  - Multiple distinct push identities or cross-host head/base → stop and retain the worktree pending configuration resolution.\nCONTEXT: Design D6.4 and D5 base/head derivation.", s.acceptanceCriteria = ["The skill opens the pull request via the forge's pull-request tool using the dedup tuple (head branch in head repository, base branch in base repository) and never creates a duplicate for an open matching PR", "Open/merged PRs are reported and the flow continues; closed-unmerged asks the user (declining both options stops and retains the worktree)", "Forge unreachable, multiple distinct push identities, or cross-host head/base are handled per the design (manual handoff or stop-and-retain)"], s.taskRefs = ["6.5 In `skills/openspec-archive-change/SKILL.md`, add the pull-request step via the forge's pull-request tool: dedup on head branch in the head repository + base branch in the base repository; open → report and continue; merged → report and continue; closed-unmerged → ask user to reopen or recreate (declining both stops and retains the worktree); forge unreachable → warn and hand off manually; multiple distinct push identities or cross-host head/base → stop and retain the worktree pending configuration resolution"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "archive-pr-step", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "archive-removal", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add the final worktree removal step", s.description = "WHAT: In skills/openspec-archive-change/SKILL.md, add the final worktree removal step.\nFILES:\n  - modify: skills/openspec-archive-change/SKILL.md\nEDGE CASES:\n  - `git worktree remove` refuses on a dirty tree: non-blocking — warn, report the handoff as complete, leave the worktree for the user or a later re-run.\n  - The removal runs only after commit, push, and PR resolution; the branch refs are never deleted.\nCONTEXT: Design D6.5.", s.acceptanceCriteria = ["The skill removes the worktree with `git worktree remove` as the final cleanup step, after commit, push, and PR resolution", "A dirty-tree refusal is non-blocking: the skill warns, reports the handoff as complete, and leaves the worktree for the user"], s.taskRefs = ["6.6 In `skills/openspec-archive-change/SKILL.md`, add the final worktree removal (`git worktree remove`, refuses on dirty tree): non-blocking — on refusal, warn, report the handoff as complete, and leave the worktree for the user"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "archive-removal", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "archive-resume-dispatch", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Add the archive resume dispatch", s.description = "WHAT: In skills/openspec-archive-change/SKILL.md, add the resume dispatch for already-moved archive destinations.\nFILES:\n  - modify: skills/openspec-archive-change/SKILL.md\nEDGE CASES:\n  - Invoked with an already-moved archive destination: skip the pre-move checks and enter post-ops directly.\n  - Pin mismatch on resume: offer keep-old-pins / accept-current-configuration (user-authorized repin) / abort; only user-authorized repins replace recorded pins.\n  - A change name resolving to multiple archive directories without an explicit destination: stop and ask the user to pick the exact destination.\nCONTEXT: Design D4 resume bullet, D5 durable pins + repin.", s.acceptanceCriteria = ["The resume dispatch skips the pre-move checks for an already-moved archive destination and enters post-ops directly", "On pin mismatch the skill stops and offers keep-old-pins / user-authorized repin / abort, and revalidates pins before any remote-touching step", "Multiple archive directories for one change name without an explicit destination stop the flow with a prompt"], s.taskRefs = ["6.7 In `skills/openspec-archive-change/SKILL.md`, add the resume dispatch: invoked with an already-moved archive destination → skip the pre-move checks and enter post-ops directly; revalidate the durable pins (remote name, endpoints, base identity, fresh default-branch resolution) with a user-authorized repin option on mismatch; multiple archive directories for one name without an explicit destination → stop and ask the user"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "archive-resume-dispatch", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Verify documentation facts, spec scenarios, and skill wording", s.description = "WHAT: Verify the documentation facts against the repo, check each skill's git steps against the change-branching spec scenarios, run the test suites, and confirm harness-neutral wording and credential safety.\nFILES:\n  - modify: (none; verification only, fix issues found in AGENTS.md, README.md, skills/, docs/ as needed)\nEDGE CASES:\n  - Any mismatch found between docs and repo state must be fixed in the same story.\n  - bb test suites must run from the worktree root.\nCONTEXT: AGENTS.md derived-fact rule; spec change-branching scenarios; design D8.", s.acceptanceCriteria = ["Documentation facts (branching strategy in AGENTS.md/README.md, derived-fact lines) verified against the repository, with any mismatch fixed", "Each skill's git steps satisfy the change-branching spec scenarios (convention detection, ensure-worktree, preflight, post-ops outcome contract) and both `bb scripts/test_story_driver.clj` and `bb scripts/test_config_merge.clj` pass", "All added git instructions are harness-neutral (no opencode-specific tool names) and no raw endpoint strings (with potential credentials) appear in any skill output, log, or report path"], s.taskRefs = ["7.1 Verify the documentation facts (2.1–2.3) against the repository per the derived-fact rule", "7.2 Check each skill's git steps against the `change-branching` spec scenarios (convention detection, ensure-worktree, preflight, post-ops outcome contract) and confirm the babashka test suites still pass: `bb scripts/test_story_driver.clj` and `bb scripts/test_config_merge.clj`", "7.3 Confirm every added git instruction in the four skills uses harness-neutral wording (generic tool names, per `AGENTS.md`) and never names opencode-specific tools", "7.4 Confirm no raw endpoint strings (with potential credentials) appear in any skill output, log, or report path — only canonical identities or fingerprints"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MERGE (s:Story {id: "archive-this-change", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
ON CREATE SET s.title = "Run the archive flow for this change", s.description = "WHAT: Run the archive flow for branch-per-change using the archive skill implemented in the previous stories: pre-move checks, sync, archive move, then post-ops (commit, push change/branch-per-change to origin, open or resolve the pull request, remove the worktree).\nFILES:\n  - modify: openspec/changes/branch-per-change/ (archived to openspec/changes/archive/YYYY-MM-DD-branch-per-change/ by the flow)\nEDGE CASES:\n  - The apply loop must NOT tick this task in advance; the archive flow ticks it in the moved tasks.md within its attributable archive commit, and completion is defined by the flow's success report (branch pushed, PR opened or resolved, worktree removed).\n  - A failed flow leaves the worktree and state file in place and resumes via the archive resume dispatch.\n  - The final checkpoint bookkeeping: after the flow succeeds, the remaining task checkbox state is recorded per the checkpoint model.\nCONTEXT: Design Migration Plan step 2 (first archive under the new flow); tasks.md task 8.1 checkpoint model.", s.acceptanceCriteria = ["The archive flow for branch-per-change completes: the change directory is moved into openspec/changes/archive/ and the post-ops success report states the branch was pushed to origin, the pull request was opened or resolved, and the worktree was removed (or non-blocking refusal was reported)", "Task 8.1 was not ticked in advance by the apply loop; its checkbox state follows the checkpoint model in the flow's attributable commit", "The default branch receives the change's work only when the pull request merges; no merge was performed by the archive flow"], s.taskRefs = ["8.1 Run the archive flow for `branch-per-change`; the checkpoint model is: the apply loop must NOT tick this task in advance — the archive flow ticks it in the moved `tasks.md` as part of its attributable archive commit, and completion is defined by the flow's success report (branch pushed, pull request opened or resolved, worktree removed); a failed flow leaves the worktree and state file in place and is resumed via the archive resume dispatch"], s.status = "pending";
MATCH (c:Change {name: "branch-per-change", project: "github.com/aplacaba/agent-skills"}),
      (s:Story {id: "archive-this-change", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (c)-[:HAS_STORY]->(s);

MATCH (a:Story {id: "docs-agents-branching", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "bootstrap-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "docs-readme-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "bootstrap-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "repo-plumbing", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "bootstrap-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "bootstrap-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "propose-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "apply-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "sync-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "archive-pre-move-state", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "shared-git-helpers", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "archive-commit-push", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-pre-move-state", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "archive-pr-step", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-commit-push", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "archive-removal", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-pr-step", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "archive-resume-dispatch", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-pr-step", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "docs-agents-branching", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "docs-readme-worktree", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "repo-plumbing", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "propose-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "apply-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "sync-worktree-skill", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-pre-move-state", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-commit-push", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-pr-step", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-removal", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "archive-resume-dispatch", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);

MATCH (a:Story {id: "archive-this-change", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MATCH (b:Story {id: "verify-implementation", change: "branch-per-change", project: "github.com/aplacaba/agent-skills"})
MERGE (a)-[:DEPENDS_ON]->(b);