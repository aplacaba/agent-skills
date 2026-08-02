## MODIFIED Requirements

### Requirement: Step with confirmation

The story-execution capability SHALL pause after each completed story and confirm with the user before implementing the next story, rather than running the full loop uninterrupted. Auto mode SHALL be activated only by passing the `--auto` flag to the story command; no environment variable or configuration setting SHALL activate it. When `--auto` is passed, the capability SHALL skip the inter-story confirmation and continue automatically; auto mode SHALL NOT bypass any other stop — Phase 1 `stories.md` approval, ambiguous-story pauses, MCP-unreachable stops, blocked runs, and completion all behave as in interactive mode. Interactive confirmation SHALL remain the default.

#### Scenario: Confirmation requested

- **WHEN** a story completes and another story is runnable, in interactive mode
- **THEN** the loop reports the result and asks the user to confirm before starting the next story

#### Scenario: Auto-approve skips confirmation

- **WHEN** a story completes and another story is runnable, in auto-approve mode
- **THEN** the loop proceeds to the next story without pausing for confirmation

#### Scenario: Auto-approve still stops on other conditions

- **WHEN** `--auto` is passed and the user has not yet approved `stories.md`, a story is ambiguous, the MCP server is unreachable, no story is runnable, or all stories are complete
- **THEN** the loop stops or reports exactly as in interactive mode
