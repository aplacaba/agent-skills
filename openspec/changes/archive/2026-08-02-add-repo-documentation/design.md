## Context

The repo is a distribution of OpenSpec agent tooling: canonical content (`skills/`, `commands/`, `agents/`, `scripts/`) with per-harness adapters (opencode, Claude Code, Codex), a babashka-based story-driver CLI, and a `setup.sh` installer. There is no top-level entry point for humans (`README.md`) or for agents (`AGENTS.md`). Existing documentation lives in `docs/install.md` and `docs/harness-mapping.md`; the OpenSpec workflow and repository conventions are only encoded inside skill/command files.

## Goals / Non-Goals

**Goals:**
- Root `README.md` that lets a newcomer understand the project, install it, and find deeper docs.
- Root `AGENTS.md` that states the binding conventions agents must follow when working in this repo.
- Both files must stay consistent with the canonical content they describe (no drift).

**Non-Goals:**
- No changes to existing files, specs, skills, commands, scripts, or setup behavior.
- No new docs beyond the two root files (existing `docs/` stays the detailed reference).

## Decisions

### D1: Content split — README for humans, AGENTS.md for agents

`README.md` covers: what the project is (OpenSpec agent tooling, story-driven apply workflow, per-harness distribution), prerequisites (babashka, openspec CLI, docker, git), quick start (`./setup.sh`), an orientation to the story-driven workflow, repo layout table, and pointers to `docs/install.md` (full install, Neo4j MCP env vars) and `docs/harness-mapping.md` (generic tool wording per harness).

`AGENTS.md` covers: the mandatory OpenSpec workflow (no code changes without a proposal; propose → apply → verify → archive), conventional commit style, harness-neutral wording rules for canonical content, the canonical directory layout, the exact script/test invocations (`bb <repo>/scripts/story_driver.clj <cmd>`, `bb scripts/test_story_driver.clj`, `bb scripts/test_config_merge.clj`), and the MCP server note (mcp/neo4j-cypher via docker).

*Alternatives considered:* a single merged README (rejected: agents and humans need different information; agent conventions belong in AGENTS.md which harnesses read automatically). Deep-linking instead of summarizing (rejected: both files need enough self-contained orientation to be useful standalone, while deferring detail to `docs/`).

### D2: Single source of truth for facts — derived vs newly established

Two categories of content go into the new files, treated differently:

- **Derived facts** (prerequisites, script paths, test commands, MCP server image, directory layout, workflow steps, tool wording): SHALL be copied from the current canonical content (`setup.sh`, skills, `commands/`, `docs/`) at write time and verified by the tasks below, so the new files do not introduce drift.
- **Newly established conventions** (the "no code changes without a proposal" hard rule and the conventional commit style): not currently stated anywhere in the repo, so their exact meaning is defined here and in AGENTS.md as the binding statement. AGENTS.md SHALL be the canonical statement of these conventions for agents; the README does not repeat them.

The conventional commit style SHALL be: `<type>(<scope>): <summary>` with a body, where `<type>` is one of `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `perf`, or `build`; `<scope>` is optional and names the affected area (e.g., `scripts`, `setup`, `docs`, `openspec`); the summary is imperative, lowercase, under 80 characters; the body explains why, not just what. Examples: `feat(scripts): port story-driver CLI to babashka`, `docs: update references from Python story-driver to babashka`. This matches the commit style already used in the repo's history (e.g., the migrate-scripts-to-babashka change was committed with exactly such messages).

### D3: Drift management

For **derived behavioral facts**, the new files are point-in-time entry points, not the authority: canonical files (`setup.sh`, skills, `docs/`) remain authoritative for behavior. README/AGENTS.md state only stable facts and link to `docs/` for detail. Any future change that alters a derived fact stated in either file SHALL update the corresponding line in the same change (enforced by the "verify" habit in AGENTS.md).

For **newly established conventions**, AGENTS.md IS the authority (per D2) — D3 does not apply to them. The initial tasks verify every derived fact against the current repo before writing.

## Risks / Trade-offs

- [Drift between README/AGENTS.md and canonical content over time] → canonical files remain authoritative; both new files link to `docs/` for detail and state only stable facts; D3 requires fact updates to ship in the same change as the behavior change.
- [AGENTS.md misleads harnesses with wrong tool names] → AGENTS.md uses generic tool phrasing consistent with `docs/harness-mapping.md`; verified in tasks.
- [Newly established conventions (no-proposal rule, commit style) diverge from practice] → AGENTS.md states the conventions as binding. The tasks verify the commit-style subject pattern and the cited examples against the repo's actual git history; the remaining constraints (mandatory body, complete allowed-type list, 80-char limit) are newly established policy. The no-proposal rule matches the observed repository pattern (all prior work went through openspec changes).

## Migration Plan

1. Read `setup.sh`, `skills/openspec-story-driver/SKILL.md`, `commands/*.md`, `docs/install.md`, `docs/harness-mapping.md` to extract exact facts.
2. Write `README.md`.
3. Write `AGENTS.md`.
4. Verify: every fact (prereqs, paths, commands) in both files matches the current repo state.

## Open Questions

- None.
