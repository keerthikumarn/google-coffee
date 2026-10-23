#!/usr/bin/env bash
# Reports how many commits touch both source and tests, and lists feat/fix/refactor commits that
# have no test. Read-only: it never blocks anything. Run it from anywhere inside the repository.
#
#   source = backend/src/main/**, frontend/src/** (excluding tests)
#   tests  = any src/test/** path, any *.test.js / *.test.jsx
#
# Docs, CI and chore commits legitimately touch neither, so a low overall ratio is expected;
# the list at the end is what matters. A fix with nothing to test (for example a renamed config
# file) can say so in the commit body, e.g. "Tests: none, config rename".
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

git log --no-merges --name-only --format='@%h %s' | awk '
/^@/ { if (n) { total++; if (s && t) both++; if (!(s && t) && type ~ /^(feat|fix|refactor)/) bad = bad "\n  " subject }
       n = 1; s = t = 0; subject = substr($0, 2); type = substr($0, 10); next }
NF  { if ($0 ~ /(src\/test\/|\.test\.jsx?$)/) t = 1; else if ($0 ~ /(backend\/src\/main\/|frontend\/src\/)/) s = 1 }
END { if (n) { total++; if (s && t) both++; if (!(s && t) && type ~ /^(feat|fix|refactor)/) bad = bad "\n  " subject }
      printf "%d of %d commits touch both source and tests\n", both, total
      if (bad != "") printf "feat/fix/refactor commits without a test:%s\n", bad }'
