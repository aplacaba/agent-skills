---
name: openspec-archive-change
description: Archive a completed change in the experimental workflow. Use when the user wants to finalize and archive a change after implementation is complete.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.6.0"
---

Archive a completed change in the experimental workflow.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`). Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name. If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Worktree convention and post-ops**

This skill follows the worktree-per-change convention using the shared git helpers defined in the propose skill (convention detection, shared preflight, ensure-worktree). When the convention is inactive, warn once and skip all git steps — the archive still completes. All change work (including the archive move) happens inside the worktree `.worktrees/<name>/`; paths are relative to the worktree root.

**Pre-move checks (before the archive move):**

1. Run the shared preflight (main checkout always; change worktree when it exists).
2. Run ensure-worktree — the worktree must exist and be on exactly `change/<name>`.
3. Record the durable post-ops state BEFORE the first remote-touching operation, in a state file under the git common directory (`git rev-parse --git-common-dir`), written atomically (temp file + rename):
   - the exact archive destination `openspec/changes/archive/YYYY-MM-DD-<name>/`;
   - the D5 pins: the selected remote name (or the explicit no-remote state), the effective push endpoint list (`git remote get-url --push --all <remote>`; the fetch URL when no push URL is configured), the base repository identity (canonical identity of the fetch endpoint), and the resolved base branch (freshly resolved via `git ls-remote --symref <remote> HEAD`, falling back to local `master`/`main`);
   - endpoint URLs are reduced to canonical identities (`<lowercase-host>[:port]/<path-segments>`, scheme-agnostic, userinfo/query/fragment stripped) or non-secret SHA-256 fingerprints for unparseable endpoints (e.g. `file://`, local paths) — raw endpoint strings never appear in the state file, reports, or logs.

**Post-ops (resumable git handoff after the move):**

The post-ops is resumable: a re-run determines its starting point from the repository state.

0. **Preflight** — shared preflight (main checkout always; worktree when it exists).
1. **Locate state** — if the worktree `.worktrees/<name>/` exists, revalidate it (ensure-worktree) and run the post-ops steps below. If it is missing (already removed), verify remote state against every pinned endpoint and the pull-request status on the forge: an open or merged matching pull request means the handoff already finished (report its URL; a merged PR waives branch presence); a branch present at the pushed tip on every endpoint with no matching pull request means the pull request can be opened now; a missing or stale branch on any endpoint without an open/merged pull request is an anomaly — stop and report.
2. **Attributable commit** (inside the worktree) — inspect `git status --porcelain`. Attributable paths — the only ones staged without asking:
   - both sides of the archive move — `openspec/changes/<name>/` (moved-away source, tracked deletions) and `openspec/changes/archive/YYYY-MM-DD-<name>/` (destination) — staged together as a rename, conditional on the source still being tracked: if `git ls-files` reports files under `openspec/changes/<name>/`, run `git add -A <source> <destination>`, otherwise `git add -A <destination>` only (re-run-safe);
   - the `openspec/specs/<capability>/spec.md` files matching the change's delta capabilities;
   - the concrete file paths named in the change's `tasks.md` — validated with `git ls-files --error-unmatch` (tracked) or existence (untracked); paths that do not exist are skipped and reported, never passed to `git add`.
   Any other dirty path triggers a prompt with three continuations: (a) approve — stage it, mark it user-approved in the report; (b) decline — stop the post-ops and report (the archive itself is already complete); (c) exclude — the user commits or stashes it manually, then re-runs. If the tree is clean, skip the commit. Commit with `chore(openspec): archive change <name>` (body explaining why).
3. **Push** — push `change/<name>` to every effective push endpoint (from the pins), then verify the branch exists at the pushed tip on EVERY pinned endpoint; a verification failure stops the flow before the pull-request step and removal. With no remote configured, skip the push, the pull-request step, and the worktree removal with a warning and report the local state (the worktree stays for the user).
4. **Pull request** (forge-side, does not need the worktree) — open the pull request `change/<name>` → base branch via the forge's pull-request tool (see `docs/harness-mapping.md`), against the base repository (fetch endpoint) and head repository (effective push endpoint). Deduplication tuple: head branch `change/<name>` in the head repository, base branch = the resolved base branch in the base repository. Outcomes:
   - open matching pull request → report its URL, create no duplicate, continue;
   - merged matching pull request → report it as merged, continue (handoff complete; branch presence waived);
   - closed-unmerged matching pull request → ask the user to reopen it or create a new one; if the user declines both options, stop the post-ops and retain the worktree;
   - forge unreachable or not pull-request-capable → warn, report the pushed branch (name + URL if derivable), tell the user to open the pull request manually, continue;
   - multiple distinct push identities, or a head-repository host different from the base-repository host (cross-forge) → stop and retain the worktree pending configuration resolution, then re-run.
5. **Remove the worktree** (final cleanup, from the main checkout) — `git worktree remove <path>`; it refuses on a dirty tree. This runs only after commit, push, and pull-request resolution (created, found, or explicitly handed off for manual follow-up). A refusal is non-blocking: warn, report the handoff as complete, and leave the worktree for the user or a later re-run. The branch refs (local and remote) are never deleted by the archive flow; they remain until the pull request merges.
6. **Report** — report the pull request URL and status, and remind the user that merging happens through the pull request.

**Resume dispatch (for an already-moved archive destination):**

- When the archive flow is invoked with an already-moved archive destination (recorded in the durable state file, or a change name resolving to exactly one archive directory), SKIP the pre-move checks and the artifact/task/sync checks, and enter the post-ops directly (step 0). A change name resolving to multiple archive directories without an explicit destination stops and asks the user to pick the exact destination.
- Revalidate the durable pins before any remote-touching step: the remote name still exists; the effective push endpoint list still matches (canonical identities or fingerprints); the base repository identity still matches; and a FRESH default-branch resolution still matches the recorded base. On any mismatch, stop and offer the user: (a) keep the old pins, (b) accept the current configuration as the new pins (user-authorized repin — record them before any remote operation resumes), or (c) abort. Only user-authorized repins replace recorded pins.
- A failed post-ops leaves the worktree and state file in place; a re-run locates state (post-ops step 1) and continues from there.

**Steps**

1. **If no change name provided, prompt for selection**

   Run `openspec list --json` to get available changes. Use the **question tool** to let the user select.

   Show only active changes (not already archived).
   Include the schema used for each change if available.

   **IMPORTANT**: Do NOT guess or auto-select a change. Always let the user choose.

2. **Check artifact completion status**

   Run `openspec status --change "<name>" --json` to check artifact completion.

   Parse the JSON to understand:
   - `schemaName`: The workflow being used
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context
   - `artifacts`: List of artifacts with their status (`done` or other)

   **If any artifacts are not `done`:**
   - Display warning listing incomplete artifacts
   - Use **question tool** to confirm user wants to proceed
   - Proceed if user confirms

3. **Check task completion status**

   Read the tasks file (typically `tasks.md`) to check for incomplete tasks.

   Count tasks marked with `- [ ]` (incomplete) vs `- [x]` (complete).

   **If incomplete tasks found:**
   - Display warning showing count of incomplete tasks
   - Use **question tool** to confirm user wants to proceed
   - Proceed if user confirms

   **If no tasks file exists:** Proceed without task-related warning.

4. **Assess delta spec sync state**

   Use `artifactPaths.specs.existingOutputPaths` from status JSON to check for delta specs. If none exist, proceed without sync prompt.

   **If delta specs exist:**
   - Compare each delta spec with its corresponding main spec at `openspec/specs/<capability>/spec.md`
   - Determine what changes would be applied (adds, modifications, removals, renames)
   - Show a combined summary before prompting

   **Prompt options:**
   - If changes needed: "Sync now (recommended)", "Archive without syncing"
   - If already synced: "Archive now", "Sync anyway", "Cancel"

   If user chooses sync, use the skill tool to invoke openspec-sync-specs for change '<name>'. Delta spec analysis: <include the analyzed delta spec summary>"). Proceed to archive regardless of choice.

5. **Perform the archive**

   Create an `archive` directory under `planningHome.changesDir` if it doesn't exist:
   ```bash
   mkdir -p "<planningHome.changesDir>/archive"
   ```

   Generate target name using current date: `YYYY-MM-DD-<change-name>`

   **Check if target already exists:**
   - If yes: Fail with error, suggest renaming existing archive or using different date
   - If no: Move `changeRoot` to the archive directory

   ```bash
   mv "<changeRoot>" "<planningHome.changesDir>/archive/YYYY-MM-DD-<name>"
   ```

6. **Display summary**

   Show archive completion summary including:
   - Change name
   - Schema that was used
   - Archive location
   - Whether specs were synced (if applicable)
   - Note about any warnings (incomplete artifacts/tasks)

**Output On Success**

```
## Archive Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** the archive path derived from `planningHome.changesDir`/YYYY-MM-DD-<name>/
**Specs:** ✓ Synced to main specs (or "No delta specs" or "Sync skipped")

All artifacts complete. All tasks complete.
```

**Guardrails**
- Always prompt for change selection if not provided
- Use artifact graph (openspec status --json) for completion checking
- Don't block archive on warnings - just inform and confirm
- Preserve .openspec.yaml when moving to archive (it moves with the directory)
- Show clear summary of what happened
- If sync is requested, use openspec-sync-specs approach (agent-driven)
- If delta specs exist, always run the sync assessment and show the combined summary before prompting
