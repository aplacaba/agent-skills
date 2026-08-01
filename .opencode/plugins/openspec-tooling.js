/**
 * opencode adapter for the openspec agent tooling.
 *
 * Auto-registers the canonical root `skills/` directory into opencode's
 * `skills.paths` via the config hook, so opencode discovers all skills
 * without symlinks. Commands and agents are registered through the
 * `.opencode/commands` and `.opencode/agents` symlinks in this directory.
 */

import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Canonical skills live at the repo root (two levels above .opencode/plugins/).
const canonicalSkillsDir = path.resolve(__dirname, '../../skills');

export default async () => {
  return {
    config: (config) => {
      config.skills = config.skills || {};
      config.skills.paths = config.skills.paths || [];
      if (!config.skills.paths.includes(canonicalSkillsDir)) {
        config.skills.paths.push(canonicalSkillsDir);
      }
    },
  };
};
