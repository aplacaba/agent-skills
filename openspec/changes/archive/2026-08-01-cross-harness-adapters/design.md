## Context

The repo currently stores all agent tooling under `.opencode/` (skills, commands, agents, scripts), which opencode auto-discovers. This layout is harness-specific: Claude Code expects a `.claude-plugin/` manifest, Codex expects `.codex-plugin/`, and neither reads opencode's layout. We want the same content available in opencode, Claude Code, and Codex without duplicating or drifting.

The reference implementation for multi-harness distribution is the installed `superpowers` plugin, which ships one canonical `skills/` tree plus thin per-harness manifests (`.claude-plugin/`, `.codex-plugin/`, `.cursor-plugin/`, `.opencode/plugins/*.js`, `gemini-extension.json`). Its opencode plugin injects the canonical skills dir into config via the `config` hook (`config.skills.paths.push(...)`), so no symlinks are needed.

## Goals / Non-Goals

**Goals:**
- Establish a canonical, harness-agnostic content root: `skills/`, `commands/`, `agents/`, `scripts/`.
- Register that content in opencode, Claude Code, and Codex through thin adapter manifests that reference the canonical paths.
- Zero content duplication across harnesses; fixes to a skill are made once.
- Keep `/opsx-*` and `openspec-story-driver` working in opencode after the move.
- OpenSpec project data (`openspec/`) stays repo-local and untouched.

**Non-Goals:**
- Porting to Cursor or Gemini CLI in this change (adapter stubs may note them, but no manifests).
- Building an npm package or release pipeline (the repo stays a git repo installed directly).
- Changing skill content semantics — this is a layout/distribution change only.

## Decisions

### D1. Canonical content moves to repo root: `skills/`, `commands/`, `agents/`, `scripts/`
Move each existing `.opencode/skills/<name>/` → `skills/<name>/`, `.opencode/commands/*.md` → `commands/*.md`, `.opencode/agents/openspec-reviewer.md` → `agents/openspec-reviewer.md`, and `.opencode/skills/openspec-story-driver/scripts/` → `scripts/`.
- *Rationale:* every harness adapter can point at these neutral paths; root-level dirs match superpowers' layout and are what Claude Code/Codex manifests expect (`"skills": "./skills/"`).

### D2. Harness-agnostic canonical content
Strip opencode-only tokens from canonical files:
- Remove `allowed-tools:` frontmatter from SKILL.md files (opencode-only). Where tool restrictions matter, each harness adapter applies its own.
- Replace opencode-specific tool invocations (`AskUserQuestion`, opencode tool names) in command/skill bodies with generic phrasing ("the question tool", "the file-edit tools").
- Keep a tool-mapping note per harness (see D4).
- *Rationale:* content must be interpretable by every harness; per-harness specifics belong in adapters.

### D3. opencode adapter: `.opencode/` + `opencode-plugin.js`
opencode reads `.opencode/` but can also discover canonical content via `skills.paths`. Design:
- Keep `.opencode/plugin.js` (auto-discovered) that injects `config.skills.paths = [<repo>/skills]` via the `config` hook, mirroring superpowers' plugin.
- Keep `.opencode/commands/` and `.opencode/agents/` as thin, content-free registration? **No** — opencode only scans `.opencode/command(s)/` and `.opencode/agent(s)/` for those. So `.opencode/commands/*.md` and `.opencode/agents/*.md` must remain real files. Decision: keep canonical files at root AND make `.opencode/commands/` + `.opencode/agents/` symlinks (or thin wrappers) into the root dirs.
  - *Symlink approach:* `.opencode/commands` → `../commands`, `.opencode/agents` → `../agents`, `.opencode/skills` → `../skills`. Single source, zero copies, git-tracked symlinks.
  - *Fallback:* if symlinks are undesirable on some OS, generate wrappers via setup script. Default: symlinks.
- opencode continues to discover skills either via `.opencode/skills` symlink or the plugin's `skills.paths`.

### D4. Claude Code adapter: `.claude-plugin/`
- `.claude-plugin/plugin.json` with `name`, `description`, `version`, `author`, `license`, `keywords`, `repository`; skills/commands/agents are discovered from the plugin's root-relative `./skills/`, `./commands/`, `./agents/` dirs (mirroring superpowers).
- `.claude-plugin/marketplace.json` for local/dev install (`"source": "./"`).
- Add a `TOOL-MAPPING.md` (or per-harness notes) in each skill or a root `docs/harness-mapping.md` so Claude Code knows the opencode→generic→Claude tool equivalences.

### D5. Codex adapter: `.codex-plugin/`
- `.codex-plugin/plugin.json` with `"skills": "./skills/"` (the field superpowers uses). Commands/agents registered per Codex conventions if supported; otherwise skills only.
- Same tool-mapping note from D4.

### D6. Setup/install documentation
- Document install per harness: opencode via plugin entry in `opencode.json`; Claude Code via marketplace/plugin install; Codex via plugin manifest.
- Keep dotfiles strategy: `setup.sh` (or docs) symlinks global config to this repo for local use.

## Risks / Trade-offs

- **Symlinks in git** → Cross-platform concern on Windows. Mitigation: document `core.symlinks=true`; keep setup script that can generate wrappers as fallback.
- **opencode tool names leaking into canonical content** → The tool-mapping note (D4) and a content lint check in tasks prevent regression.
- **Claude/Codex manifest schema drift** → Manifests are small and static; validate against each harness's documented schema in tasks. Risk → keep manifests minimal (name/version/skills path only).
- **Breakage of existing `/opsx-*` during move** → All moves are `git mv`; verify opencode still discovers everything after the move (task 4).
- **Story-driver script path references** → SKILL.md references `story-driver.py`; after moving to `scripts/`, update the referenced path in canonical content. Risk → migration task explicitly updates the path.

## Migration Plan

1. `git mv` all content to canonical root dirs (skills/, commands/, agents/, scripts/).
2. Update `openspec-story-driver/SKILL.md` and `opsx-*.md` for the new `scripts/` path and stripped harness tokens.
3. Add `.opencode/plugin.js`, `.opencode` symlinks, `.claude-plugin/`, `.codex-plugin/`.
4. Verify: opencode discovers all skills/commands/agents (run a discovery check); manifests point at existing paths.
5. Rollback: reverse the `git mv`s; delete adapter dirs. No data loss (everything remains in git).

## Open Questions

- Whether opencode auto-discovers `.opencode/plugin.js` in addition to the `plugin:` config array (the `customize-opencode` skill says `.opencode/plugin/` and `.opencode/plugins/` are auto-discovered; if `.opencode/plugin.js` is not auto-discovered, it must be referenced from the repo's `opencode.json`).
- Whether Codex supports command/agent registration or only skills via plugin.json in the current version.
