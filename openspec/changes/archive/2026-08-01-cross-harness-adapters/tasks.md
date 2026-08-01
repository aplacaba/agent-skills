## 1. Canonical content restructure

- [x] 1.1 `git mv` all `.opencode/skills/<name>/` → `skills/<name>/`
- [x] 1.2 `git mv` all `.opencode/commands/*.md` → `commands/*.md`
- [x] 1.3 `git mv` `.opencode/agents/openspec-reviewer.md` → `agents/openspec-reviewer.md`
- [x] 1.4 `git mv` `.opencode/skills/openspec-story-driver/scripts/` → `scripts/` and delete the now-empty nested dirs
- [x] 1.5 Remove `allowed-tools:` frontmatter from all canonical SKILL.md files
- [x] 1.6 Replace harness-specific tool names (e.g., `AskUserQuestion`) with generic phrasing in canonical commands and skills
- [x] 1.7 Update `openspec-story-driver/SKILL.md` script path references from `<skill-dir>/scripts/` to the canonical `scripts/` location

## 2. opencode adapter

- [x] 2.1 Add `.opencode/plugin.js` that injects the canonical `skills` dir into `config.skills.paths` (mirroring the superpowers plugin)
- [x] 2.2 Replace `.opencode/commands` and `.opencode/agents` with symlinks to `../commands` and `../agents`
- [x] 2.3 Confirm opencode auto-discovery of `.opencode/plugin.js` or add it to the repo's `opencode.json` plugin array if needed

## 3. Claude Code and Codex adapters

- [x] 3.1 Create `.claude-plugin/plugin.json` registering the canonical skills, commands, and agents
- [x] 3.2 Create `.claude-plugin/marketplace.json` for dev install
- [x] 3.3 Create `.codex-plugin/plugin.json` with `"skills": "./skills/"`
- [x] 3.4 Add a harness tool-mapping note (e.g., `docs/harness-mapping.md`) referenced by canonical content

## 4. Verification

- [x] 4.1 Verify opencode discovers all skills, commands, and agents after the move (no missing `/opsx-*`)
- [x] 4.2 Verify the `story-driver.py` script runs from its new canonical path
- [x] 4.3 Validate the Claude Code and Codex plugin manifests against each harness's manifest schema
- [x] 4.4 Confirm `openspec/specs/` and OpenSpec project data are untouched by the restructure
