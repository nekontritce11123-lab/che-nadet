#!/usr/bin/env bash
# Explicit, create-only GitHub publication. Refuses unverified/dirty sources.
set -euo pipefail
cd "$(dirname "$0")/.."
die() { printf '%s\n' "$*" >&2; exit 2; }
for tool in git gh adb; do command -v "$tool" >/dev/null || die "Required tool missing: $tool"; done
git rev-parse --is-inside-work-tree >/dev/null || die 'Restore the Git bundle first.'
[[ -f app/build.gradle.kts ]] && grep -q 'app.chenadet' app/build.gradle.kts || die 'Not the CheNadet Android candidate.'
[[ -z "$(git status --porcelain)" ]] || die 'Commit/review changes before publication; checkout must be clean.'
if git remote get-url github >/dev/null 2>&1; then die 'Remote github already exists; no existing remote is modified.'; fi
[[ "$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')" -ge 1 ]] || die 'Connect an authorized Android device or start an emulator first.'
gh auth status
owner="$(gh api user --jq .login)"
[[ "$owner" =~ ^[A-Za-z0-9-]+$ ]] || die 'Could not resolve GitHub account.'
repository="$owner/che-nadet"
if gh repo view "$repository" >/dev/null 2>&1; then die 'che-nadet already exists; refusing to modify it.'; fi
# No repository write happens until all native verification commands succeed.
./gradlew --no-daemon clean :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest
[[ -z "$(git status --porcelain)" ]] || die 'Verification changed tracked sources. Review before publishing.'
gh repo create "$repository" --public --description 'Чё надеть? Android weather and explainable clothing recommendations; no AI.'
git remote add github "https://github.com/$repository.git"
git push github HEAD:refs/heads/main
gh repo edit "$repository" --default-branch main
printf 'Published verified candidate: https://github.com/%s\n' "$repository"
