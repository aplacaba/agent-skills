# AGENTS.md

Binding conventions for AI agents working in this repository. Read this file
before making any change.

## OpenSpec workflow (mandatory)

Every code change requires a matching OpenSpec proposal before any code is
written:

1. **Propose** — create artifacts under `openspec/changes/<change-name>/`
   (proposal, design, specs, tasks) and have them reviewed before freezing.
2. **Apply** — implement the tasks from `tasks.md`; no file modifications
   without a proposal.
3. **Verify** — after implementation, check that the implementation matches
   the proposal.
4. **Archive** — sync delta specs into `openspec/specs/` and move the change
   to `openspec/changes/archive/YYYY-MM-DD-<name>/`.

Hard rules:

- Bug fixes do not require a proposal; feature changes and new features do.
- No proposal, no change: if you are asked to modify code, confirm a matching
  proposal exists first, or create one.
- No coding in explore mode: when exploring, do not create proposals, edit
  files, or write tests.
- Files outside the current proposal's scope must not be modified.
- After a change is complete, run the verify flow before archiving.

## Conventional commits

Use conventional commit messages:

```
<type>(<scope>): <summary>
```

- `<type>`: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `perf`, or
  `build`.
- `<scope>`: optional, names the affected area (e.g. `scripts`, `setup`,
  `docs`, `openspec`).
- `<summary>`: imperative, lowercase, under 80 characters.
- A body explaining why (not just what) is included.

Examples:

- `feat(scripts): port story-driver CLI to babashka`
- `docs: update references from Python story-driver to babashka`

## Branching strategy

Each proposed OpenSpec change works in a dedicated git worktree at
`.worktrees/<name>/` on branch `change/<name>`:

- The worktree and branch are created at propose time, before any change
  artifact is written, and the main checkout is never switched; it stays on
  the default branch.
- All work for a change (proposal artifacts, implementation commits, spec
  syncs) happens inside its worktree.
- The default branch receives proposed-change commits only when the change's
  pull request merges. Bug fixes (which require no proposal) are exempt and
  may be committed directly on the default branch.
- The archive step performs the post-archive git steps: commit remaining
  work, push the change branch to the remote, open a pull request against the
  default branch, and remove the worktree. The branch (local and remote) is
  kept until the pull request merges; the archive flow never merges it.
- This convention is executed manually by the agent following this file — no
  bundled skill enforces it, and the stock per-repo `openspec init` workflow
  does not automate worktrees.

## Harness-neutral canonical content

Canonical content in `skills/`, `commands/`, and `agents/` must not contain
harness-specific tool tokens (e.g. `allowed-tools:` frontmatter or
opencode-specific tool names). Use generic wording (e.g. "the question tool",
"the todo tracking tool") and reference
[docs/harness-mapping.md](docs/harness-mapping.md) where tool mapping matters.

## Repository layout

| Path | Contents |
|---|---|
| `skills/` | Canonical skills (story-driven apply) |
| `commands/` | Canonical `/opsx-story` command definition |
| `agents/` | Agent definitions (the openspec reviewer) |
| `scripts/` | Babashka helper scripts + tests |
| `docs/` | Install and harness tool mapping |
| `.worktrees/` | Per-change git worktrees (gitignored) |
| `openspec/specs/` | Main specifications |
| `openspec/changes/` | Active and archived changes |
| `setup.sh` | Global opencode installer |

## Scripts and tests

Story-driven mechanics (parsing tasks, validating story definitions,
generating `stories.md` + vault notes, polling for the next runnable story,
setting statuses, classifying projects, toggling task checkboxes, appending
state):

```bash
bb <repo>/scripts/story_driver.clj <command> ...
```

- `parse-tasks <tasks.md> [--json]`
- `generate <change> --project <name> [--root <changeRoot>] [--def <stories.yaml>] [--vault <path>]`
- `next <change> --project <name> [--vault <path>]`
- `set-status <change> <storyId> <status> --project <name> [--vault <path>]`
- `classify <project> [--type <t>] [--tech-stack <a,b>] [--repo-url <u>] [--vault <path>]`
- `sync-tasks <change> <storyId> [--root <changeRoot>] [--def <stories.yaml>]`
- `append-state <change> <text> [--root <changeRoot>]`

Run the test suites from the repo root:

```bash
bb scripts/test_story_driver.clj
bb scripts/test_rev_swap.clj
```

Reviewer-model preset switching (one-command swaps of `cheap`/`work`/`hybrid`
across the opencode reviewer agent frontmatter and the codex `config.toml`):

```bash
bb <repo>/scripts/rev_swap.clj [cheap|work|hybrid]
```

No-arg invocation prints current settings; `-h` lists presets. `setup.sh`
installs it as `~/.local/bin/rev-swap`.

The story graph lives in an Obsidian vault of markdown notes (`Stories/` +
`Projects/`), resolved via the `OBSIDIAN_VAULT` environment variable (default
`~/obsidian/obsidian`) or the `--vault` flag. No MCP server is required. If a
harness registers an Obsidian MCP server (e.g. `obsidian-mcp@2` with the vault
allowlisted), the story-driver skill uses its read tools for note reads; every
write goes through `story_driver.clj`. See `docs/install.md` for per-harness
registration and `docs/harness-mapping.md` for tool names.

## Documentation facts

`README.md` and this file state derived facts (prerequisites, script paths,
commands, layout). If a change alters any of those facts, update the
corresponding line in `README.md` or `AGENTS.md` in the same change and verify
it against the repository.
