## proposal Round 1 — 2026-08-02
### 🔴 Fixed
 - Contradiction with repo-documentation "Existing files unchanged" requirement → delta now explicitly removes/replaces it
 - Enforcement scope limited to propose+archive → apply and sync added (verify-on-branch)
 - Repo-only policy vs globally distributed skills → policy scoped repo-local; skills implement guarded generic git steps
 - Absolute master policy vs bug-fix exception → bug-fix exemption added
 - Missing archive fail-safe contract → committed in proposal, deferred to design
 - No bootstrap rule → Bootstrap section added (move to change/branch-per-change before apply)
### 🟡 Addressed
 - Remote-branch expectations → resolved in design (defer), noted in proposal
### 🔴 Outstanding
 - (none)

## proposal Round 2 — 2026-08-02
### 🔴 Fixed
 - Bug-fix exception still contradicted absolute master statements → all master statements now qualified to "proposed changes"
### 🔴 Outstanding
 - (none)

## proposal Round 3 — 2026-08-02
### 🔴 Fixed
 - (none)
### 🔴 Outstanding
 - (none) — proposal batch passes, frozen

## design Round 1 — 2026-08-02
### 🔴 Fixed
 - Repo-local guard undefined → D2 convention detection (git repo + `## Branching strategy` marker in AGENTS.md)
 - Branch creation base → D3 ensure-branch switches to default branch first
 - Apply/sync enforcement → shared ensure-branch, stop instead of wrong-branch continuation
 - Bootstrap contradiction → commit only change dir; unrelated dirty files stay uncommitted
 - No-remote/outcome contract → outcome table; no remote skips push/remote-delete only
 - Default-branch detection → runtime algorithm (remote HEAD normalized, master/main fallback)
 - Remote delete fail-safety → tip-containment check
### 🔴 Outstanding
 - Propose/dirty-tree sequencing; missing-branch vs frozen proposal; archive branch check timing; attribution scope

## design Round 2 — 2026-08-02
### 🔴 Fixed
 - Propose sequencing → ensure-branch before scaffolding; only propose creates missing branch
 - Missing branch → warn+ask, never create late; archive checks branch pre-move
 - Attribution → attributable-path rule; no broad dir staging
### 🔴 Outstanding
 - Archive move: stage both sides as rename; proposal timing conflict (decision-level → unfroze proposal)

## design Round 3 — 2026-08-02 (proposal unfrozen+re-frozen for timing)
### 🔴 Fixed
 - Archive move staging → conditional `git add -A <source> <destination>` with git ls-files guard
 - Proposal timing → branch created before scaffold (proposal re-frozen)
### 🔴 Outstanding
 - Re-run safety of staging; per-path vs per-file attribution; wrong-branch continuation vs proposal

## design Round 4 — 2026-08-02
### 🔴 Fixed
 - Missing branch → STOP in convention-active repos (no continuation, no creation)
 - Staging re-run-safe → ls-files condition
 - Remote tip fetch before ancestor check
### 🔴 Outstanding
 - Dirty-tree cross-contamination; same-file attribution claim

## design Round 5 — 2026-08-02
### 🔴 Fixed
 - Branch-switch dirty rules → change-branch switches only with change-dir-only dirt; default-branch switches need clean tree
 - Attribution per path, not per directory; task-listed path validation
### 🔴 Outstanding
 - Hunk-level attribution claim; prompt continuation undefined

## design Round 6 — 2026-08-02
### 🔴 Fixed
 - Attribution claim → honest per-path limits + mitigations
 - Prompt continuations → approve/decline/exclude defined
### 🔴 Outstanding
 - Post-ops resumability after switch to default branch

## design Round 7 — 2026-08-02
### 🔴 Fixed
 - Post-ops resumable state machine (step 0 locate state; merge re-run; push retry)
 - D2 exact marker; merge commit conventional subject
### 🔴 Outstanding
 - Resume entry point after archive move; in-progress MERGE_HEAD handling

## design Round 8 — 2026-08-02
### 🔴 Fixed
 - Resume entry point → archive flow dispatches to post-ops for archived changes (full destination path)
 - MERGE_HEAD sub-states → unresolved/resolved/fresh merge
### 🔴 Outstanding
 - Remote delete race; preflight vs in-progress git ops; multi-archive destination ambiguity

## design Round 9 — 2026-08-02
### 🔴 Fixed
 - Preflight (step 0) stops on active merge/rebase/cherry-pick/revert
 - Remote delete conditional on re-validated hash
 - Resume uses exact archive destination; ambiguity stop
 - Index validation before completing pending merge
### 🔴 Outstanding
 - (none) — design batch FORCE-FROZEN by user decision at cap+ (option A)

## specs Round 1 — 2026-08-02
### 🔴 Fixed
 - Post-ops conflict recovery contradiction (preflight vs managed merge) → design D6 step 0 exception (MERGE_HEAD at change branch tip = managed, completed at step 5); spec + outcome table aligned
 - Push-failure vs no-remote outcomes encoded in spec; deletion scenarios made conditional
 - Full failure contract added to spec (wrong branch, archive ambiguity, task-path validation, index validation, -d refusal, fetch/ancestor failure, remote-delete warning)
 - README delta enumerates branch facts (creation, master/bug-fix policy, post-archive steps)
### 🔴 Outstanding
 - Delta heading, outcome-row contradiction, unsafe-tip retention wording

## specs Round 2 — 2026-08-02
### 🔴 Fixed
 - `## Requirements` → `## ADDED Requirements`
 - Design outcome table row aligned with managed-merge exception
 - Unsafe remote tip retains only remote branch (local already deleted)
### 🔴 Outstanding
 - (none) — specs batch passes, frozen

## design (worktree rework) — 2026-08-02 — REVIEW STOPPED BY USER
The user made two decision-level adjustments during this batch:
 1. PR-based post-ops (push branch + create PR; no archive-time merge/delete)
 2. Worktree-per-change instead of branch switching (.worktrees/<name>/ on change/<name>; main checkout never switched)
Both required unfreezing proposal → design → downstream. Proposal was re-reviewed and re-frozen; design went through multiple rounds (R1–R16). User stopped the design review here ("that's enough for review"). Remaining open items at stop: durable-pin timing (two-stage record), state-file keying by archive destination, closed-unmerged vs endpoint-tip ordering in the no-worktree path, port-aware cross-forge compatibility, pushed-tip OID in durable state, git-common-dir portability. These are recorded here and NOT resolved; design.md is treated as accepted-as-is by user decision.

## finalize — 2026-08-02 (user: "finalize proposal")
- specs/ rewritten for the worktree+PR model (were frozen under the stale branch-switching/merge/delete model): change-branching (7 requirements: worktree per change, convention detection, ensure-worktree, phase enforcement, remote/default-branch selection, post-archive handoff, skill neutrality) and repo-documentation delta (README + AGENTS MODIFIED to worktree wording, REMOVED "Existing files unchanged").
- tasks.md rewritten for the worktree+PR model (8 groups, 22 tasks).
- design.md accepted as-is by user decision; review stopped (open items recorded above).
- Proposal phase finalized: no further reviewer rounds per user decision; change is apply-ready pending a tasks/specs review round if desired.
