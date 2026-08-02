## proposal Round 1 — 2026-08-02
### 🔴 Fixed
 - harness-adapters omitted → added as modified (seed parity + CLI contract)
 - metadata path undefined → --project flag + agent MCP registration split
### 🟡 Addressed
 - identity key = Project.name; reclassification SET-on-MERGE; opsx-story.md impact; legacy migration out of scope
### 🔴 Outstanding
 - --project optional vs guaranteed ownership; Change identity collision

## proposal Round 2 — 2026-08-02
### 🔴 Fixed
 - --project required; Change identity project-scoped {name, project}
### 🟡 Addressed
 - vocabulary deferred to design; AGENTS.md added to impact
### 🔴 Outstanding
 - Story nodes not project-scoped; direct Story lifecycle queries not covered

## proposal Round 3 — 2026-08-02
### 🔴 Fixed
 - Story identity {id, change, project}; HAS_STORY/DEPENDS_ON match on all three; ALL story-graph queries project-filtered
### 🟡 Addressed
 - legacy: registration classifies Project only, no legacy linking
### 🔴 Outstanding
 - Project identity not deterministic; story-execution omitted from capabilities

## proposal Round 4 — 2026-08-02
### 🔴 Fixed
 - project name = git origin normalized owner/repo
 - story-execution added to modified capabilities + impact
### 🔴 Outstanding
 - owner/repo insufficient across hosts; no-origin fallback undefined

## proposal Round 5 — 2026-08-02
### 🔴 Fixed
 - project name = host/owner/repo; fallback local/<dir>
### 🟡 Addressed
 - seeding/resume guard folded into all-queries-scoped bullet; legacy exclusion marked BREAKING
### 🔴 Outstanding
 - local fallback collision-unsafe

## proposal Round 6 — 2026-08-02
### 🔴 Fixed
 - local/<dir> trade-off documented explicitly + agent guidance to prefer origin
 - story-graph summary includes readiness + seeding/resume guard
### 🟡 Addressed
 - normalization boundary deferred to design
### 🔴 Outstanding
 - fallback identity stability wording

## proposal Round 7 — 2026-08-02
### 🔴 Fixed
 - identity stability clarified (origin changes start new project identity, no migration)
### 🔴 Outstanding
 - none → batch frozen

## design Round 1 — 2026-08-02
### 🔴 Fixed
 - D1 normalization boundary made exact (forms, strips, subgroup join, lowercase, unparseable defined; repoUrl ≠ identity)
 - D4 full-overwrite semantics (SET all three, null clears); repoUrl credential stripping
### 🟡 Addressed
 - techStack normalization (lowercase/dedup/sort/aliases); D3 parser edge cases; ownership statement literal form
### 🔴 Outstanding
 - subgroup rules contradictory; query/fragment credential path

## design Round 2 — 2026-08-02
### 🔴 Fixed
 - sole identity rule host/<all-but-last>/<last> (subgroups preserved)
 - repoUrl strips userinfo + query + fragment; HTTPS normalization/SSH→HTTPS known forges/omit otherwise
### 🟡 Addressed
 - fallback basename lowercased
### 🔴 Outstanding
 - none (shorthand Cypher snippet cleanup recommended)

## design Round 3 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - misleading shorthand snippet replaced with literal statement form
### 🔴 Outstanding
 - none → batch frozen

## specs Round 1 — 2026-08-02
### 🔴 Fixed
 - queries scoped to {change, project} (readiness, execution, blocked, completion); direct Story updates {id, change, project}
 - classification requirement now carries D1/D4 contracts (origin forms, unparseable boundary, type vocab, techStack normalization, repoUrl sanitization incl. query/fragment)
 - seed parity → "updated golden baseline" wording
 - opsx-story scenario requires canonical name derivation from git origin
### 🔴 Outstanding
 - none (HTTPS .git retention detail added round 2)

## specs Round 2 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - repoUrl HTTPS retained form including .git suffix per D4
### 🔴 Outstanding
 - none → batch frozen

## tasks Round 1 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - 3.1 gains D4 full-overwrite/null-clearing semantics
 - 3.3 requires canonical D1 derivation from git origin
 - 1.4 shows --project as required; 2.3 adds flag-as-value case; new 4.3 doc verification
### 🔴 Outstanding
 - none → batch frozen
