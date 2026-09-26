# Development ledger

26.09.2026: source specification complete; autonomous inline execution.
GitHub authenticated, no repository-create action. Remote device absent. No SDK/Gradle, container DNS unavailable.
Ruling: continue in isolated local new repository; never label a local commit as pushed.

Stage 2: contract tests first: 19/25 groups failed on initial no-rule implementation (core-red.log outside Git). Implemented rules and normalized optional inputs. `scripts/check-core.sh`: 25 groups, 4092 assertions, exit 0. Core compiles with Kotlin 1.9.0 / JDK 21 offline, target JVM 17. This does not verify Android.
