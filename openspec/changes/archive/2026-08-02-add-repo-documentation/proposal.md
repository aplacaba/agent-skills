## Why

The repo has no top-level entry point: no `README.md` for humans (what this project is, how to install and use it) and no `AGENTS.md` for AI agents working in the repo (workflow rules, conventions, and command references). Newcomers and agent harnesses must reverse-engineer the project from `docs/install.md`, `docs/harness-mapping.md`, and the skill files, which is slow and error-prone.

## What Changes

- Add `README.md` at the repo root: project overview (openspec agent tooling for opencode/Claude Code/Codex), quick install via `./setup.sh`, prerequisites (babashka, openspec CLI, docker, git), how the story-driven workflow works, repo layout, and pointers to `docs/install.md` / `docs/harness-mapping.md`.
- Add `AGENTS.md` at the repo root: mandatory conventions for AI agents working in this repo — the OpenSpec propose→apply→verify→archive flow (no code changes without a proposal), conventional commit style, harness-neutral wording rules, where the canonical content lives (`skills/`, `commands/`, `agents/`, `scripts/`), the babashka script invocation (`bb <repo>/scripts/story_driver.clj`), and the test commands (`bb scripts/test_story_driver.clj`, `bb scripts/test_config_merge.clj`).
- No existing files modified, no breaking changes.

## Capabilities

### New Capabilities

- `repo-documentation`: provides a root-level `README.md` describing the project, install, usage, and layout, plus a root-level `AGENTS.md` stating the working conventions agents must follow in this repo.

### Modified Capabilities

- None.

## Impact

- New files: `README.md`, `AGENTS.md` at the repo root.
- No changes to existing skills, commands, scripts, specs, or setup behavior.
- Documentation only — no dependency, API, or runtime impact.
