# Contributing

Small, self-contained changes keep this project reviewable and its history useful.
Every change should tell one story and prove it with tests.

## The rules

1. **One logical change per commit.** A feature, a fix, a refactor: each gets its own commit (or its own small PR).
2. **Tests travel with the change.** A `feat` or `fix` commit includes the tests that pin the new behavior. A fix starts with a test that fails without it.
3. **Don't mix kinds of work.** Formatting, refactoring and new behavior never share a commit.
   - `refactor` commits must not change behavior, and must not edit existing tests.
   - Formatting-only changes go in their own `style` commit, or are skipped.
4. **Keep it small.** Aim for a PR under ~400 changed lines. If it is bigger, split it.
5. **Every commit builds and passes tests.** No "fix the previous commit" follow-ups; amend before pushing.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`

| Type | Use for |
|---|---|
| `feat` | New behavior (with tests) |
| `fix` | Bug fix (with a regression test) |
| `refactor` | Restructure without behavior change |
| `test` | Tests only (e.g. covering existing behavior) |
| `docs` | Documentation only |
| `chore` | Tooling, dependencies, build |
| `ci` | CI/CD pipeline |
| `style` | Formatting only |

- Summary in the imperative, lowercase, no trailing period, about 72 characters or fewer.
- Scope is optional: `order`, `menu`, `pg`, `frontend`, `ai`, `ci`.
- The body explains *why*, not what; the diff already shows what.

Examples:

```
fix(order): reject status change from DELIVERED back to PREPARING
feat(menu): hide sold-out items from the customer menu
refactor(service): extract ETA queue-depth lookup
```

## Running the tests

```bash
# backend
cd backend && ./mvnw -B test

# frontend
cd frontend && npm test
```

Both suites must pass before you push. GitHub Actions runs them (plus the production image build) on every push and pull request. The backend tests need Docker for the PostgreSQL adapter tests; see the README for details.

## Pull requests

Use the PR template. Describe the one change, link the tests that prove it, and
note anything deliberately left out.

> Commit-message and test-presence conventions are currently guidance only;
> nothing blocks a non-conforming commit. Please follow them anyway.
