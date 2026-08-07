# /implement

Implement exactly one TASK. Update its tests and tick it in the tasklist.

Write the test first — this repo is test-driven and the security-relevant tests need a negative
control (a test that fails when the protection is removed).

Record deviations in the task itself: if you reused something instead of adding it, or skipped a
dependency, say so in the task bullet.

Then `/pre-commit` and commit as `<TICKET> TASK-N: <short>`.
