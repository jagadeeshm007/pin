# Contributing

Thanks for helping. Pin is built by people working with AI coding agents, so the
process is written down and the same for both.

1. Pick a task from `docs/roadmap.md` or open an issue proposing one.
2. Follow `docs/sdlc/README.md`: spec, plan and tasks before code for features;
   a reproduction and a regression test for bugs.
3. Branch from `main`: `<type>/PIN-<n>-<slug>`.
4. Commits: `feat(voice): add VAD gate [PIN-22]` (Conventional Commits with the task id).
5. Run `make check` before pushing. CI runs the same.
6. Open a pull request using the template; include the acceptance-criteria
   evidence from `verify.md`.

Device testing matters here. If you test on a phone, write the model and
Android version in `verify.md`.

Using an agent: point it at `AGENTS.md`. Claude Code users get the stage
commands `/spec`, `/plan`, `/tasks`, `/build`, `/verify`, `/review`.
