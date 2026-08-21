---
name: openspec-story-driver
description: Drive OpenSpec change implementation through a story graph stored as markdown notes in an Obsidian vault. Decompose a change into fully-specified stories (max 3 acceptance criteria each), write them into the vault, then continuously poll for the next runnable story, implement it, compact context, and repeat until the change is complete.
license: MIT
compatibility: Requires openspec CLI, babashka, and an Obsidian vault (path via OBSIDIAN_VAULT or --vault). Optionally, a harness-registered Obsidian MCP server is used for reading notes; it is never required.
metadata:
  author: openspec
  version: "1.0"
---

# Story-Driven Apply

Run an OpenSpec change through a story graph stored as markdown notes in an Obsidian vault. The workflow decomposes a change's `tasks.md` into stories with at most 3 acceptance criteria and explicit dependency chains, then executes them one at a time with context compaction between stories.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`). Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: Optionally specify a change name (e.g., `/opsx-story add-auth`). If omitted, infer from conversation or `openspec list --json`. Announce "Using change: <name>". Optionally pass `--auto` to run the loop without pausing for confirmation between stories (interactive confirmation remains the default).

## Prerequisites

### Obsidian vault

Story state lives in an Obsidian vault as plain markdown notes. The vault path is resolved as: `--vault <path>` flag → `OBSIDIAN_VAULT` environment variable → `~/obsidian/obsidian`. The vault directory must exist and be writable; only the `Stories/` and `Projects/` descendants are created by the tooling. No plugins, no running app, no database.

### Obsidian MCP (optional)

If your harness has an Obsidian MCP server registered (see [docs/install.md](../../docs/install.md) for the per-harness setup), the workflow uses its read tools (e.g. the read-note tool) when reading story notes. It is never required: plain file reads cover the same ground, and every write always goes through the helper script. If your harness namespaces MCP tools (see [docs/harness-mapping.md](../../docs/harness-mapping.md)), address the read tool accordingly.

### Helper script

`story_driver.clj` (in the repo scripts/ directory) performs the mechanical parts: parsing tasks, validating the story definition, writing vault notes, polling for the next runnable story, updating statuses, classifying projects, toggling task checkboxes, and appending state. Run it as `bb <repo>/scripts/story_driver.clj <command> ...`.

## Workflow

### Phase 0 — Verify the vault and derive the project name

1. Confirm the vault resolves to an existing, writable directory (the `generate` command errors otherwise). If not, print guidance (set `OBSIDIAN_VAULT` or pass `--vault`) and STOP. Do not modify any files.
2. Derive the canonical project name from the repository's git `origin`:
   - Normalize the origin URL to lowercase `host/owner/repo`, preserving subgroups (`host/owner/group/repo`); strip scheme, userinfo, port, `.git` suffix, trailing slashes, query strings, and fragments. Accept HTTPS (`https://host/owner/repo[.git]`), SSH scp-style (`git@host:owner/repo[.git]`), and SSH URL (`ssh://[user@]host[:port]/owner/repo[.git]`) forms.
   - If `origin` is absent or unparseable (no host, no path, fewer than two path segments, or no matching form), fall back to lowercase `local/<repo-directory-name>` and prefer setting `origin` so the name becomes canonical.
   - The same repository always yields the same project name; pass it as `--project <project-name>` to the subcommands below.

### Phase 1 (resume check) — Has this change already started?

Before decomposing or generating anything, check whether a prior run exists for this change in this project:

```bash
bb <repo>/scripts/story_driver.clj next "<name>" --project <project-name>
```

- **"change folder not found"** → no prior run; proceed to Phase 2 (decompose).
- **Any story with status `in_progress` or `done`** (`inProgressIds`/`counts.done`) → a run is in progress; skip decomposition and regeneration entirely and go to Phase 5 (execution loop).
- **Folder exists but every story is `pending`** → an interrupted pre-approval run; proceed to Phase 2 — regeneration is status-preserving, so all-pending state is rewritten safely.

### Phase 2 — Decompose the change into stories

Read the change's context: `proposal.md`, `design.md`, `specs/**`, and `tasks.md`. Then decompose `tasks.md` into a story definition.

1. Parse tasks so none are missed:
   ```bash
   openspec status --change "<name>" --json
   bb <repo>/scripts/story_driver.clj parse-tasks openspec/changes/<name>/tasks.md
   ```
2. Write `<changeRoot>/stories.yaml` following the **Zero-Assumption Story Template** below. Group related tasks into stories; each story maps to the task descriptions it covers via `taskRefs`. Combined, `taskRefs` must cover every task exactly once (no orphans, no extras).
3. Every story MUST satisfy the completeness checklist:
   - Exact file paths to create/modify (relative to repo root).
   - Exact behavior to implement, stated so an agent needs no judgment call.
   - Edge cases named explicitly.
   - At most 3 acceptance criteria, each objectively checkable.
4. Enforce dependency chains: a story may `dependsOn` stories that must finish first. Never create cycles. Dependencies are same-change only.
5. If a task set needs more than 3 acceptance criteria, split it into multiple stories.
6. Generate and review:
   ```bash
   bb <repo>/scripts/story_driver.clj generate "<name>" --project <project-name> --root <changeRoot>
   ```
   This writes `stories.md` (human-readable review copy) and the vault notes (story notes, `_change.md`, project note). Fix any validation errors it reports. All notes are `pending` at this point; nothing is "started".
7. Show the user `stories.md` for review. Pause for approval. If rejected, fix `stories.yaml` and re-run `generate` — existing statuses are preserved, so nothing is lost.

**Zero-Assumption Story Template (use in `stories.yaml`):**

```yaml
change: <change-name>
stories:
  - id: <kebab-case-unique-id>
    title: <short title>
    description: |
      WHAT: <exact behavior to implement>
      FILES:
        - create: <exact path>
        - modify: <exact path>
      EDGE CASES:
        - <edge case 1>
        - <edge case 2>
      CONTEXT: <pointer to relevant design/spec section if needed>
    acceptanceCriteria:
      - <criterion 1>          # max 3, objectively checkable
      - <criterion 2>
    dependsOn:
      - <story-id-that-must-finish-first>
    taskRefs:
      - "<exact task description text from tasks.md>"
```

### Phase 3 — Classify the project (optional but recommended)

Register the project in the vault so changes are linked to their owning repository with classification metadata. Derive the classification facts from the repository:
- `type`: one of `tooling`, `agent`, or `docs`, from the README's intent; omit when not derivable.
- `techStack`: lowercase list of runtime/language names from manifests (`package.json`, `deps.edn`, `Cargo.toml`, `pyproject.toml`) and directory layout; deduplicate, sort alphabetically, and normalize aliases (`js`/`javascript` → `javascript`, `ts`/`typescript` → `typescript`, `py` → `python`). Record both runtimes and languages when present (e.g. `["node", "typescript"]`).
- `repoUrl`: sanitized origin — strip userinfo, query strings, and fragments; keep `https://host/owner/repo` (retaining any `.git` suffix) for HTTPS origins; convert SSH origins to HTTPS on known forges (github.com, gitlab.com, bitbucket.org); otherwise omit. Never store credentials.

Write the classification with full-overwrite semantics for the three fields (a field passed as absent is written `null` to clear it):

```bash
bb <repo>/scripts/story_driver.clj classify <project-name> \
  [--type <type>] [--tech-stack <a,b>] [--repo-url <url>]
```

### Phase 4 — Seed the story graph

Decide whether to seed or resume, using the Phase 1 check:

- **If a run is in progress:** keep the existing vault state and go to Phase 5.
- **Otherwise:** `generate` (Phase 2 step 6) has already written the notes; re-running is idempotent (status-preserving), so re-running produces no duplicates and loses nothing.

### Phase 5 — Execution loop

Repeat until complete or blocked:

1. **Poll** for the next runnable story (deterministic, lowest `id` first, scoped to the change's project):
   ```bash
   bb <repo>/scripts/story_driver.clj next "<name>" --project <project-name>
   ```
   The JSON output has `runnable` (null when none), `counts` (`total`/`done`/`inProgress`/`pending`/`remaining`), `blocked` (pending stories with unmet dependencies), and `inProgressIds`.
2. **Stalled-run guard:** if `inProgressIds` is non-empty (a story left `in_progress` by an interrupted session), STOP and report the stalled story. The user either resumes that story or has it reset with `set-status` before the loop continues.
3. **No runnable story:** run the blocked/complete check (Phase 6).
4. **Mark in progress:**
   ```bash
   bb <repo>/scripts/story_driver.clj set-status "<name>" "<story-id>" in_progress --project <project-name>
   ```
5. **Implement** the story. Read its full note — via the Obsidian MCP read tool when registered, otherwise read the file `Stories/<project-slug>/<change-slug>/<story-id>.md` — it is fully-specified, so implement without making assumptions. If the description is ambiguous, PAUSE and ask — do not guess. Keep changes minimal and scoped.
6. **Verify** each acceptance criterion against the implementation.
7. **Mark done** and synchronize:
   ```bash
   bb <repo>/scripts/story_driver.clj set-status "<name>" "<story-id>" done --project <project-name>
   ```
   Then sync the task checkboxes:
   ```bash
   bb <repo>/scripts/story_driver.clj sync-tasks "<name>" "<story-id>" --root <changeRoot>
   ```
   And append a compact summary:
   ```bash
   bb <repo>/scripts/story_driver.clj append-state "<name>" "<story-id>: <one-line summary of what changed and key decisions>" --root <changeRoot>
   ```
8. **Compact context.** You are about to move to a new story. Compress the current session context (drop implementation details that are recorded in `.story-state.md`). Before polling the next story, reload the state summary from `<changeRoot>/.story-state.md`.
9. **Confirm with the user** before starting the next story. Report what completed and what is next. If the run was started with `--auto`, skip the confirmation and continue to the next story automatically; all other stops (Phase 2 `stories.md` approval, ambiguity, vault failure, stalled run, blocked, complete) still apply. If the user says continue, return to step 1; otherwise stop here.

### Phase 6 — Blocked and complete detection

When `next` returns `runnable: null`:

- **If `counts.remaining = 0`:** all stories are complete. Report completion and suggest running the archive step (`/opsx-archive`).
- **If `counts.remaining > 0`:** the dependency chain is blocked. List the blocked stories (`blocked` in the `next` output) and STOP with a clear message.

## Vault Layout Reference

```
<V> = vault root (OBSIDIAN_VAULT | --vault | ~/obsidian/obsidian)
<V>/Stories/<project-slug>/<change-slug>/<story-id>.md   story note
<V>/Stories/<project-slug>/<change-slug>/_change.md      change note
<V>/Projects/<project-slug>.md                            project note
```

- **Story note** — YAML frontmatter `id`, `title`, `change`, `project`, `status` (`pending` | `in_progress` | `done`); body: trimmed description, then `## Acceptance criteria`, `## Depends on` (wikilinks `[[Stories/<project-slug>/<change-slug>/<dep-id>|<dep-id>]]`), and `## Tasks` sections when non-empty.
- **Change note** (`_change.md`) — frontmatter `name`, `project`; body `# <name>` + `## Stories` links.
- **Project note** — frontmatter `name`, `type`, `techStack`, `repoUrl`; body `# <name>` + `## Changes` links.
- **Slugs** — lowercase, runs of non-`[a-z0-9._-]` replaced with `-`, collapsed, trimmed. Story ids are kebab-case and slug-identical.

Readiness rule: a story is runnable when `status = pending` and none of its `## Depends on` targets have a status other than `done`. All script calls are scoped to the change's project via `--project`.

## Guardrails

- Verify vault reachability before any action; never proceed silently into a broken state.
- Confirmation between stories applies in interactive mode only; `--auto` skips it (the loop still stops on ambiguity, vault failure, stalled runs, blocked runs, and completion).
- Stories must have at most 3 acceptance criteria and zero-assumption descriptions; pause if a story is ambiguous rather than guessing.
- `taskRefs` across all stories must cover every `tasks.md` task exactly once.
- Never regenerate over an in-progress run; resume the existing vault state instead (the resume check runs before anything else).
- Single-writer assumption: statuses and notes are written only by the script; treat concurrent edits inside the vault as user edits to respect.
- Compact context after each story and reload `.story-state.md` before polling the next.
- Update task checkboxes and story status promptly when a story completes.
- Stop and report when blocked; suggest archive when complete.
