# Development ledger

26.09.2026: source specification complete; autonomous inline execution.
GitHub authenticated, no repository-create action. Remote device absent. No SDK/Gradle, container DNS unavailable.
Ruling: continue in isolated local new repository; never label a local commit as pushed.

Stage 2: contract tests first: 19/25 groups failed on initial no-rule implementation (core-red.log outside Git). Implemented rules and normalized optional inputs. `scripts/check-core.sh`: 25 groups, 4092 assertions, exit 0. Core compiles with Kotlin 1.9.0 / JDK 21 offline, target JVM 17. This does not verify Android.

Stage 3: 17 new test groups failed before implementation. Implemented provider-normalization boundary without JSON/network dependencies, UTC alignment, interval conversion, unit checks, city parsing, freshness/cache/request policies and Russian explanations. `scripts/check-core.sh`: 45 groups / 4186 assertions, exit 0. All API fixtures are synthetic; no live HTTP verification claimed.

Regression pass: stale cached forecast was anchored to download time. Four new checks reproduced failures: stale future rain, contradictory apparent values reversing extreme-cold/heat outfits, saturated humidity outside the fallback formula domain. Added a distinct evaluation timestamp, defensive clothing bounds and a humid-heat safety rule. RED 4 groups → GREEN 49 groups / 4194 assertions. Native one-time-permission history reset has an Android unit test written; not executed without SDK/Gradle.

Ruling: keep Android integration on development/android-v1; main remains the verified Kotlin core baseline until a native build can be run. This is not a claim of a completed Android release.
