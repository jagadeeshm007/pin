@AGENTS.md

# Claude Code specifics

- Use plan mode for any change touching more than three files, the action
  registry, the planner contract, the history schema or permissions.
- Slash commands for the lifecycle live in `.claude/commands/`:
  `/spec`, `/plan`, `/tasks`, `/build`, `/verify`, `/review`.
- Rules in `.claude/rules/` load with the files they cover.
- Bound long command output with `| tail -40`.
- After two failed approaches to the same problem, stop and report.

## Things Claude gets wrong here

Add a line each time a mistake repeats; delete lines that stop applying.

- Piping `make check` into `grep` or `tail` hides its exit code. Run gates with
  `set -o pipefail`, or commit in a separate command after reading the result.
- Chaining `make check ...; git commit` with `;` commits even when the gate fails
  (happened again in PIN-30). Join the gate and the commit with `&&`, or commit in a
  separate call after reading `check: passed`.
