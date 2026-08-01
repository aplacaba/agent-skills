## Why

The story-driven apply workflow (and the openspec skill set) currently lives only in opencode's `.opencode/` layout, so it cannot be used in other agent harnesses like Claude Code or Codex. We want one canonical set of skills/commands/agents that is registered by thin per-harness adapters, so the same content works across harnesses without duplication or drift.

## What Changes

- **Restructure** the repo from a single `.opencode/` layout into a canonical root layout plus per-harness adapter directories:
  - Canonical content moves to root-level `skills/`, `commands/`, `agents/`, `scripts/` (harness-agnostic).
  - `.opencode/` becomes the opencode adapter (registration only, not the canonical home).
  - New `.claude-plugin/` adapter for Claude Code (plugin.json + marketplace.json).
  - New `.codex-plugin/` adapter for Codex (plugin.json with `skills` path).
- **BREAKING**: Move existing `.opencode/skills/*`, `.opencode/commands/*`, `.opencode/agents/*`, and `.opencode/skills/openspec-story-driver/scripts/*` to the canonical root locations.
- Remove harness-specific tokens (`allowed-tools:` frontmatter, `AskUserQuestion`) from canonical skill content so it is interpretable by any harness; each adapter supplies harness-specific registration.
- Add `openspec/changes/cross-harness-adapters/` as the tracking change for this work.
- OpenSpec project data (`openspec/`) remains untouched and repo-local.

## Capabilities

### New Capabilities
- `harness-adapters`: Registers the canonical skills/commands/agents in each target harness (opencode, Claude Code, Codex) via thin adapter manifests, with no duplicated content and no cross-harness drift.

### Modified Capabilities
<!-- No existing spec-level behavior changes; openspec/specs/ is empty and this is a distribution/layout change. -->

## Impact

- **New directories**: `.claude-plugin/`, `.codex-plugin/`, root `skills/`, `commands/`, `agents/`, `scripts/`.
- **Moved files**: All skills, commands, agents, and the story-driver script move out of `.opencode/` into canonical root locations; `.opencode/` becomes an adapter with registration references.
- **New dependency**: none. Harness manifests are static JSON/markdown.
- **Workflow**: `/opsx-*` commands and the `openspec-story-driver` skill continue to work in opencode via the adapter; same content becomes available in Claude Code and Codex.
