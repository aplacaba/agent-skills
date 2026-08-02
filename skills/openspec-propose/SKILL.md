---
name: openspec-propose
description: Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.6.0"
---

Propose a new change - create the change and generate all artifacts in one step.

I'll create a change with artifacts:
- proposal.md (what & why)
- design.md (how)
- tasks.md (implementation steps)

When ready to implement, run /opsx-apply

---

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`). Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: The user's request should include a change name (kebab-case) OR a description of what they want to build.

---

**Shared git helpers (defined here, used by the propose, apply, sync, and archive skills)**

These three procedures are the single source of truth for the worktree-per-change git convention. The other skills reference them instead of redefining them.

1. **Convention detection** — the worktree-per-change convention is active only when BOTH hold:
   - the working directory is a git repository (`git rev-parse` succeeds), and
   - the repository's root `AGENTS.md` contains the exact heading `## Branching strategy` with the documented `change/<name>` worktree convention.
   When the convention is inactive, warn once and skip all git steps without failing the workflow. A missing change worktree in a convention-active repository is a workflow violation that stops the phase — never evidence that the convention is inactive.

2. **Shared preflight** — refuse to proceed (stop and report) while any git operation is in progress — an active merge, rebase, cherry-pick, or revert (detected via `MERGE_HEAD`, `rebase-merge`/`rebase-apply`, `CHERRY_PICK_HEAD`, or `REVERT_HEAD` state, or unmerged paths in `git status`). Git-operation state is checkout-specific, so the preflight runs in the main checkout always, and additionally in the change worktree only when that worktree still exists. It runs before every git step in every phase, so a commit can never accidentally complete a pre-existing operation.

3. **Ensure-worktree** — verify the change worktree at `.worktrees/<name>/` exists and is a registered git worktree on exactly branch `change/<name>` using `git worktree list --porcelain` (match the path and its branch). A stale directory at that path, or a worktree on another branch, stops and reports. Only the propose phase creates the worktree; apply, sync, and archive stop and report when it is missing.

---

**Steps**

1. **If no clear input provided, ask what they want to build**

   Use the **question tool** (open-ended, no preset options) to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Create the change worktree (worktree-per-change convention)**

   Run the shared preflight, then resolve the default branch (from the selected remote's HEAD via `git ls-remote --symref <remote> HEAD`, falling back to the first existing local `master`/`main`, else stop and ask). Verify the main checkout is on that default branch; if not, stop and ask the user. Then create the worktree:

   ```bash
   git fetch <remote> <base>
   git worktree add -b change/<name> .worktrees/<name> FETCH_HEAD
   ```

   If branch `change/<name>` already exists (re-propose/resume), use `git worktree add .worktrees/<name> change/<name>` instead. The main checkout is never switched. When the convention is inactive, warn once and skip all git steps — all further steps then run in the current checkout.

3. **Create the change directory** (inside the worktree)
   ```bash
   openspec new change "<name>"
   ```
   This creates a scaffolded change in the planning home resolved by the CLI with `.openspec.yaml`.

4. **Get the artifact build order**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to get:
   - `applyRequires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts with their status and dependencies
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context. Use these instead of assuming repo-local paths.

4. **Create artifacts in sequence until apply-ready**

   Use the **todo tracking tool** to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Get instructions:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - The instructions JSON includes:
        - `context`: Project background (constraints for you - do NOT include in output)
        - `rules`: Artifact-specific rules (constraints for you - do NOT include in output)
        - `template`: The structure to use for your output file
        - `instruction`: Schema-specific guidance for this artifact type
        - `resolvedOutputPath`: Resolved path or pattern to write the artifact
        - `dependencies`: Completed artifacts to read for context
      - Read any completed dependency files for context
      - Create the artifact file using `template` as the structure and write it to `resolvedOutputPath`
      - Apply `context` and `rules` as constraints - but do NOT copy them into the file
      - Show brief progress: "Created <artifact-id>"

   b. **Continue until all `applyRequires` artifacts are complete**
      - After creating each artifact, re-run `openspec status --change "<name>" --json`
      - Check if every artifact ID in `applyRequires` has `status: "done"` in the artifacts array
      - Stop when all `applyRequires` artifacts are done

   c. **If an artifact requires user input** (unclear context):
      - Use **question tool** to clarify
      - Then continue with creation

5. **Commit the proposal on the change branch**

   Run the shared preflight again, then stage **only** the change directory and commit:

   ```bash
   git add openspec/changes/<name>/
   git commit -m "chore(openspec): propose change <name>"
   ```

   All commits include a body explaining why. When the convention is inactive, skip this step.

6. **Show final status**
   ```bash
   openspec status --change "<name>"
   ```

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions
- What's ready: "All artifacts created! Ready for implementation."
- Prompt: "Run `/opsx-apply` or ask me to implement to start working on the tasks."

**Artifact Creation Guidelines**

- Follow the `instruction` field from `openspec instructions` for each artifact type
- The schema defines what each artifact should contain - follow it
- Read dependency artifacts for context before creating new ones
- Use `template` as the structure for your output file - fill in its sections
- **IMPORTANT**: `context` and `rules` are constraints for YOU, not content for the file
  - Do NOT copy `<context>`, `<rules>`, `<project_context>` blocks into the artifact
  - These guide what you write, but should never appear in the output

**Guardrails**
- Create ALL artifacts needed for implementation (as defined by schema's `apply.requires`)
- Always read dependency artifacts before creating a new one
- If context is critically unclear, ask the user - but prefer making reasonable decisions to keep momentum
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next
