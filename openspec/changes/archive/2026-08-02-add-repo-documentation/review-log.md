## proposal Round 1 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - review-log.md created (expected for round 1)
### 🔴 Outstanding
 - none → batch frozen

## design Round 1 — 2026-08-02
### 🔴 Fixed
 - D2 claimed all facts derivable from repo sources → split derived facts vs newly established conventions; D3 drift management; risk section updated
### 🔴 Outstanding
 - D2/D3 authority conflict; conventional commit convention undefined

## design Round 2 — 2026-08-02
### 🔴 Fixed
 - D3 limited to derived behavioral facts; AGENTS.md authoritative for conventions
 - conventional commit format fully defined (type/scope/summary/body/types/80 chars) with repo-matching examples
### 🟡 Addressed
 - practice-verification claim recast (subject pattern + examples verified; rest is policy)
### 🔴 Outstanding
 - none (commit-history verification scope clarified round 3)

## design Round 3 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - history-verification scope narrowed: subject pattern + examples verified, remaining constraints are new policy
### 🔴 Outstanding
 - none → batch frozen

## specs Round 1 — 2026-08-02
### 🔴 Fixed
 - commit convention incomplete → scenario defines optional scope, imperative <80 summary, mandatory body, types, examples
 - README/AGENTS content split → README no longer claims test commands
 - MCP note missing → AGENTS.md requirement + scenario
### 🟡 Addressed
 - harness wording + canonical dirs scenario added; repo-diff scenario
### 🔴 Outstanding
 - lowercase summary missing; repo-diff not exhaustive

## specs Round 2 — 2026-08-02
### 🔴 Fixed
 - lowercase summary added; repo-diff now exclusive-additions assertion
### 🔴 Outstanding
 - D3 drift rule missing from spec; README harness names missing

## specs Round 3 — 2026-08-02
### 🔴 Fixed
 - AGENTS.md mandates fact-update rule (same change) + scenario
 - README names opencode/Claude Code/Codex; facts scenario covers workflow/tool wording
### 🔴 Outstanding
 - fact-update scenario missing verification obligation; sources still narrower than D2

## specs Round 4 — 2026-08-02
### 🔴 Fixed
 - fact-update scenario adds "verify it against the repository"
### 🟡 Addressed
 - verification sources now setup.sh, skills, commands, scripts, docs
### 🔴 Outstanding
 - none → batch frozen

## tasks Round 1 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - 4.1 scope check: distinguishes expected openspec artifacts from product-file modifications
 - 3.3 verification: adds no-proposal pattern, canonical dirs, harness-neutral wording checks
 - 1.1: running Neo4j instance classified as operational requirement, not setup prerequisite
### 🔴 Outstanding
 - none → batch frozen
