#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# openspec-tooling setup for opencode
# Installs the canonical skills/commands/agents/plugin into the global opencode
# config (~/.config/opencode) and merges the Neo4j MCP server block.
#
# Safe to re-run: symlinks are refreshed, the config merge is idempotent, and
# a backup of opencode.json(.c) is written before any edit.
# ---------------------------------------------------------------------------

# Resolve the repo root portably (BSD vs GNU readlink differ; use Python).
REPO_ROOT="$(python3 -c 'import os, sys; print(os.path.realpath(sys.argv[1]))' "$(dirname "${BASH_SOURCE[0]}")")"

# Allow overriding the global opencode config dir for testing.
OPENCODE_CONFIG_DIR="${OPENCODE_CONFIG_DIR:-$HOME/.config/opencode}"

say()  { printf '%s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
err()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Prerequisite checks
# ---------------------------------------------------------------------------
say "==> Checking prerequisites"

missing_hard=0
check_cmd() {
  local tool="$1"; shift
  if command -v "$tool" >/dev/null 2>&1; then
    say "  [ok] $tool"
    return 0
  fi
  warn "  [missing] $tool — $*"
  return 1
}

check_cmd python3 "install Python 3 (https://python.org)" || missing_hard=1
check_cmd openspec "install via: npm i -g openspec" || missing_hard=1
check_cmd node    "install Node.js (https://nodejs.org)" || missing_hard=1
check_cmd git     "install git (https://git-scm.com)" || missing_hard=1

if command -v python3 >/dev/null 2>&1; then
  if python3 -c 'import yaml' >/dev/null 2>&1; then
    say "  [ok] PyYAML"
  else
    warn "  [missing] PyYAML — install via: pip3 install pyyaml"
  fi
fi

if [ "$missing_hard" -eq 1 ]; then
  err "hard prerequisites are missing; install them and re-run ./setup.sh"
fi

# ---------------------------------------------------------------------------
# 2. Idempotent symlinking into the global opencode config
# ---------------------------------------------------------------------------
say "==> Installing into $OPENCODE_CONFIG_DIR"

mkdir -p \
  "$OPENCODE_CONFIG_DIR/skill" \
  "$OPENCODE_CONFIG_DIR/command" \
  "$OPENCODE_CONFIG_DIR/agent" \
  "$OPENCODE_CONFIG_DIR/plugins"

# Create or refresh a symlink at $link -> $target.
# Refuses to touch an existing real file/dir (not a symlink).
symlink() {
  local target="$1"
  local link="$2"
  if [ -e "$link" ] && [ ! -L "$link" ]; then
    warn "  skip $link (real file/dir exists; remove it to let setup manage it)"
    return
  fi
  ln -sfn "$target" "$link"
  say "  link $link -> $target"
}

# Skills (one directory per skill, contains SKILL.md)
for d in "$REPO_ROOT"/skills/*/; do
  [ -d "$d" ] || continue
  symlink "$d" "$OPENCODE_CONFIG_DIR/skill/$(basename "$d")"
done

# Commands (one .md file per command)
for f in "$REPO_ROOT"/commands/*.md; do
  [ -e "$f" ] || continue
  symlink "$f" "$OPENCODE_CONFIG_DIR/command/$(basename "$f")"
done

# Agent
symlink "$REPO_ROOT/agents/openspec-reviewer.md" \
        "$OPENCODE_CONFIG_DIR/agent/openspec-reviewer.md"

# opencode plugin (injects skills.paths)
symlink "$REPO_ROOT/.opencode/plugins/openspec-tooling.js" \
        "$OPENCODE_CONFIG_DIR/plugins/openspec-tooling.js"

# ---------------------------------------------------------------------------
# 3. Neo4j MCP config merge (idempotent, with backup)
# ---------------------------------------------------------------------------
say "==> Merging Neo4j MCP config"

if [ -z "${NEO4J_PASSWORD:-}" ]; then
  warn "NEO4J_PASSWORD not set — skipping Neo4j MCP merge (set it and re-run)"
else
  NEO4J_URI="${NEO4J_URI:-bolt://localhost:7687}" \
  NEO4J_USER="${NEO4J_USER:-neo4j}" \
  NEO4J_PASSWORD="$NEO4J_PASSWORD" \
  OPENCODE_CONFIG_DIR="$OPENCODE_CONFIG_DIR" \
  python3 <<'PYEOF'
import json
import os
import re
import shutil
import sys


def strip_jsonc(text):
    """Remove // and /* */ comments, preserving string contents."""
    out = []
    i, n = 0, len(text)
    in_str = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_str:
            out.append(c)
            if c == "\\":
                if i + 1 < n:
                    out.append(nxt)
                    i += 2
                    continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            i += 2
            while i < n and not (text[i] == "*" and i + 1 < n and text[i + 1] == "/"):
                i += 1
            i = min(i + 2, n)
            continue
        out.append(c)
        i += 1
    return "".join(out)


def remove_trailing_commas(text):
    """Drop commas directly before } or ] (JSONC), preserving strings."""
    out = []
    i, n = 0, len(text)
    in_str = False
    while i < n:
        c = text[i]
        if in_str:
            out.append(c)
            if c == "\\":
                if i + 1 < n:
                    out.append(text[i + 1])
                    i += 2
                    continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == ",":
            j = i + 1
            while j < n and text[j] in " \t\r\n":
                j += 1
            if j < n and text[j] in "}]":
                i = j
                continue
        out.append(c)
        i += 1
    return "".join(out)


def find_config(config_dir):
    for name in ("opencode.json", "opencode.jsonc"):
        cand = os.path.join(config_dir, name)
        if os.path.exists(cand):
            return cand
    return os.path.join(config_dir, "opencode.json")


def main():
    cfg_dir = os.environ["OPENCODE_CONFIG_DIR"]
    uri = os.environ["NEO4J_URI"]
    user = os.environ["NEO4J_USER"]
    pwd = os.environ["NEO4J_PASSWORD"]

    path = find_config(cfg_dir)
    if os.path.exists(path):
        raw = open(path).read()
        try:
            data = json.loads(remove_trailing_commas(strip_jsonc(raw)))
        except Exception as exc:  # noqa: BLE001
            sys.exit(f"could not parse {path}: {exc}")
    else:
        data = {"$schema": "https://opencode.ai/config.json"}

    if "neo4j" in data.get("mcp", {}):
        print(f"  mcp.neo4j already present in {path} — leaving unchanged")
        return

    backup = path + ".bak"
    if os.path.exists(path):
        shutil.copy2(path, backup)
        print(f"  backup written: {backup}")

    data.setdefault("$schema", "https://opencode.ai/config.json")
    data.setdefault("mcp", {})
    data["mcp"]["neo4j"] = {
        "type": "local",
        "command": [
            "npx",
            "-y",
            "@neo4j/mcp-server",
            "--uri", uri,
            "--database", "neo4j",
            "--username", user,
            "--password", pwd,
        ],
    }

    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"  merged mcp.neo4j into {path}")


main()
PYEOF
fi

# ---------------------------------------------------------------------------
# 4. Per-harness install guidance
# ---------------------------------------------------------------------------
cat <<EOF

==> opencode is installed. Restart opencode to pick up the changes.

==> Other harnesses (install separately):

  Claude Code (inside a session):
    /plugin marketplace add $REPO_ROOT
    /plugin install openspec-tooling@openspec-tooling-dev

  Codex (inside the CLI):
    /plugins
    openspec-tooling
    Install Plugin

  See docs/install.md for details and the Neo4j MCP env vars.
EOF
