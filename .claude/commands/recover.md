# /recover

Recover interrupted work: diff the tree against the tasklist, identify what actually landed, map the
remainder to a micro-task, run `/pre-commit`, commit, then push the current ticket branch.

Do not amend or rewrite existing commits — append. Do not resume the normal task loop until the
recovery commit is visible on the remote; if push fails, stop and report the blocked checkpoint.
