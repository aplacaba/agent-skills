## proposal Round 1 — 2026-08-02
### 🔴 Fixed
 - Breaking changes misidentified → marked script removal/rename, bb prerequisite, invocation change as **BREAKING**
### 🟡 Addressed
 - .gitignore speculative cache entry → concrete removal of Python cache entries
 - Why section 3 sentences → judged acceptable on re-review
### 🔴 Outstanding
 - none

## proposal Round 2 — 2026-08-02
### 🔴 Fixed
 - (none remaining)
### 🟡 Addressed
 - (none)
### 🔴 Outstanding
 - none → batch frozen

## design Round 1 — 2026-08-02
### 🔴 Fixed
 - invalid YAML API (yaml.core/safe-load-string) → clj-yaml.core/parse-string; fallback = pin bb version, no subset reader
 - babashka.test doesn't exist → clojure.test + explicit run-tests + non-zero exit
 - REPO_ROOT resolved before bb gate (set -e abort) → moved after gate, inline require babashka.fs
 - open-world babashka.cli → strict per-subcommand rejection
 - cheshire :separators unsupported → D4 hand-built cypher array literals
### 🟡 Addressed
 - min bb version → resolved in migration step 1
 - backup ambiguity → tied to actual edit
### 🔴 Outstanding
 - cheshire :separators still invalid; yaml namespace naming

## design Round 2 — 2026-08-02
### 🔴 Fixed
 - D4 rewritten: per-element generate-string + ", " join, byte-parity by construction
 - D3 namespacing fixed
### 🟡 Addressed
 - risk/fallback consistency (D3, risks, open questions aligned)
 - parser rejection now covered by tests in D5
### 🔴 Outstanding
 - golden stories.md header names story-driver.py; happy-path coverage missing for 3 subcommands

## design Round 3 — 2026-08-02
### 🔴 Fixed
 - header line named as sole intentional golden difference
 - happy paths for all four subcommands + escaping fixture added
### 🔴 Outstanding
 - parser exit code wrong (1 vs argparse 2); validation test suite incomplete

## design Round 4 — 2026-08-02
### 🔴 Fixed
 - D2: parser failures exit 2 + usage; validation failures exit 1
 - D5: full validation set enumerated
### 🟡 Addressed
 - config-merge coverage widened (malformed, new config, key preservation, json/jsonc precedence)
 - min-version = tested minimum, documented
### 🔴 Outstanding
 - top-level dispatch (no/unknown subcommand, help) unspecified; unreachable orphan-check test

## design Round 5 — 2026-08-02
### 🔴 Fixed
 - D2: root-level parser failures + help behavior specified; D5 updated
 - orphan check noted as unreachable from CLI, kept for code parity
### 🔴 Outstanding
 - no-subcommand message wrong (required: command vs invalid choice); config-merge prefix drift

## design Round 6 — 2026-08-02
### 🔴 Fixed
 - no-subcommand → 'the following arguments are required: command'; unknown → invalid choice
 - config-merge: no 'error:' prefix, parity with sys.exit(string)
### 🔴 Outstanding
 - none → batch frozen

## specs Round 1 — 2026-08-02
### 🔴 Fixed
 - /opsx-story runtime invocation uncovered → added opsx-story scenario + preserved CLI contract
 - CLI contract not fully specified → added normative contract text + scenario
 - install docs requirement missing → modified Per-harness install documentation (bb + no Python)
### 🔴 Outstanding
 - positionals/flag-applicability/help missing from contract; harness-mapping.md uncovered

## specs Round 2 — 2026-08-02
### 🔴 Fixed
 - CLI contract completed (positionals, flag applicability, help exit 0, byte-identical artifacts)
 - harness-mapping.md coverage added
### 🟡 Addressed
 - "identical in structure" → byte-identical with sole header exception
### 🔴 Outstanding
 - "where feasible" qualifier could permit drift

## specs Round 3 — 2026-08-02
### 🔴 Fixed
 - removed "where feasible" qualifier → header is sole permitted difference
### 🔴 Outstanding
 - none → batch frozen

## tasks Round 1 — 2026-08-02
### 🔴 Fixed
 - --root/--def value-less → <path> args
 - D3 gate incomplete → parse-string on nested-lists + block-scalar fixture, upgrade fallback
 - REPO_ROOT missing dirname arg → added
 - config-merge tests not in suite → 4.8 test_config_merge.clj, 8.1 runs both
 - 4.3/4.5 underspecified → file bytes compared, parser/help cases expanded
### 🟡 Addressed
 - oversized tasks split; D6 order fixed (gate before usage); 5.7 missing-bb/no-python checks; 8.2 runs setup twice
### 🔴 Outstanding
 - sync/append success stdout + unknown-story-id error untested; escaping fixture descriptions-only

## tasks Round 2 — 2026-08-02
### 🔴 Fixed
 - 3.8/3.9 success stdout; 4.3 stdout+bytes; 4.6 unknown story id
### 🟡 Addressed
 - fixture special chars in descriptions AND acceptance criteria; split 3.5/3.6, 4.5/4.6, 4.9/4.10
### 🔴 Outstanding
 - none (4.9 stderr clarification recommended)

## tasks Round 3 — 2026-08-02
### 🔴 Fixed
 - none
### 🟡 Addressed
 - 4.9 malformed-config message explicitly on stderr
### 🔴 Outstanding
 - none → batch frozen

## specs Unfreeze (bug fix: MCP server) — 2026-08-02
### 🔴 Fixed
 - @neo4j/mcp-server npm package does not exist (404) → official server is mcp-neo4j-cypher via mcp/neo4j-cypher:latest Docker image (stdio); prerequisite node → docker
 - install-setup delta: Prerequisite validation updated to bb/openspec/docker/git; Neo4j MCP config merge requirement MODIFIED in full (all 3 scenarios preserved) describing docker invocation with NEO4J_USERNAME/URI/PASSWORD container env vars
 - implementation: config-merge.clj emits docker run -i --rm -e NEO4J_URI=... -e NEO4J_USERNAME=... -e NEO4J_PASSWORD=... -e NEO4J_DATABASE=neo4j -e NEO4J_TRANSPORT=stdio mcp/neo4j-cypher:latest; setup.sh checks docker; docs/SKILL example updated; config-merge tests updated (28 assertions pass)
 - tasks.md: no task mentions node/docker → no task changes required
### 🔴 Outstanding
 - none → specs batch re-frozen; tasks batch re-frozen
