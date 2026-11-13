---
description: Self-review the branch and prepare the pull request (stage 6)
argument-hint: PIN-<n>
---

Review the branch for $ARGUMENTS.

1. `git diff main...HEAD --stat`, then read the diff.
2. Go through `docs/sdlc/review-checklist.md`. Fix failures as new commits.
3. Run `make check` once more.
4. Print the pull request title and body (summary, AC table from `verify.md`, risks) and the commands for the maintainer:
   `git push -u origin <branch>` and `gh pr create --fill`.
5. Do not push or open the pull request yourself.
