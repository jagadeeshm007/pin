# Performance budgets

From the blueprint (section 10). Baseline device: mid-range 2023 phone with
4 to 6 GB RAM. A change that moves a budget measures it in `verify.md`.

| Budget | Target | How to measure |
| --- | --- | --- |
| Idle listening CPU (VAD only) | < 2% of one core | `adb shell top -d 5`, 10 minutes |
| Process PSS, session on, ASR warm | < 150 MB | `adb shell dumpsys meminfo <pkg>` |
| Process PSS, session off | < 40 MB | same |
| Battery, idle listening, screen off | < 2% per hour | Battery Historian, 4 hours |
| Wake phrase to earcon | < 300 ms | timestamped log events |
| Speech end to local action | < 500 ms p95 | timestamped log events, 100 trials |
| Stop to all activity halted | < 150 ms | same, 20 trials |
| Planner step (cloud) | < 2.5 s p50, < 5 s p95 | history `PlannerCall.latencyMs` |
| Cold start to listening | < 1.5 s | Macrobenchmark |
| APK size, arm64 release | < 12 MB | `make size` |
| First-launch required download | < 80 MB | module catalog sizes |
| History per agent step | < 100 KB, < 300 KB with screenshot | history store metrics |
| Main thread work per accessibility event | < 4 ms | Perfetto trace |
