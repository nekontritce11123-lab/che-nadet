"""Sandboxed publication-gate tests. Never calls real GitHub, adb, Gradle or git push."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "publish-github.sh"
REAL_GIT = shutil.which("git")

class PublishGateTests(unittest.TestCase):
    def run_fixture(self, *, build_fail=False, no_device=False, existing=False, dirty=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / 'scripts').mkdir(); (root / 'app').mkdir()
            (root / 'app/build.gradle.kts').write_text('namespace = "app.chenadet"')
            self.assertTrue(SOURCE.exists(), 'publication gate not implemented')
            shutil.copy(SOURCE, root / 'scripts/publish-github.sh')
            log = root / 'calls.log'; bin_dir = root / 'tools'; bin_dir.mkdir()
            (root / '.gitignore').write_text('calls.log\ntools/\nsdk/\n')
            def executable(path, body):
                path.write_text('#!/usr/bin/env bash\nset -eu\n' + body); path.chmod(0o755)
            executable(root / 'gradlew', 'echo BUILD >> "$TEST_LOG"\nexit "${BUILD_STATUS:-0}"\n')
            executable(bin_dir / 'gh', '''case "$*" in
'auth status') exit 0;;
'api user --jq .login') echo fixture-user;;
'repo view fixture-user/che-nadet') exit "${REPO_EXISTS_STATUS:-1}";;
'repo create '*) echo CREATE >> "$TEST_LOG";;
'repo edit '*) echo EDIT >> "$TEST_LOG";;
*) exit 97;; esac
''')
            executable(bin_dir / 'adb', 'echo "List of devices attached"\nif [[ "${NO_DEVICE:-0}" == 0 ]]; then printf "emulator-fixture\\tdevice\\n"; fi\n')
            executable(bin_dir / 'git', 'if [[ "$1" == push ]]; then echo "PUSH $*" >> "$TEST_LOG"; else exec "$REAL_GIT" "$@"; fi\n')
            def git(*args): subprocess.run([REAL_GIT, '-C', str(root), *args], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            git('init', '-b', 'development/android-v1'); git('config', 'user.name', 'Fixture'); git('config', 'user.email', 'fixture@example.invalid')
            git('add', '.'); git('commit', '-m', 'fixture')
            if dirty: (root / 'unexpected.txt').write_text('uncommitted')
            env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'], TEST_LOG=str(log), REAL_GIT=REAL_GIT,
                BUILD_STATUS='1' if build_fail else '0', NO_DEVICE='1' if no_device else '0', REPO_EXISTS_STATUS='0' if existing else '1')
            result = subprocess.run(['bash', str(root / 'scripts/publish-github.sh')], env=env, cwd=root, capture_output=True, text=True)
            calls = log.read_text().splitlines() if log.exists() else []
            return result, calls
    def test_failed_native_build_cannot_create_or_push(self):
        result, calls = self.run_fixture(build_fail=True)
        self.assertNotEqual(0, result.returncode); self.assertEqual(['BUILD'], calls)
    def test_missing_device_cannot_publish(self):
        result, calls = self.run_fixture(no_device=True)
        self.assertNotEqual(0, result.returncode); self.assertEqual([], calls)
    def test_existing_repository_is_not_modified(self):
        result, calls = self.run_fixture(existing=True)
        self.assertNotEqual(0, result.returncode); self.assertNotIn('CREATE', calls)
        self.assertFalse(any(line.startswith('PUSH') for line in calls))
    def test_dirty_sources_cannot_publish(self):
        result, calls = self.run_fixture(dirty=True)
        self.assertNotEqual(0, result.returncode); self.assertEqual([], calls)
    def test_success_runs_verification_before_creation_and_push(self):
        result, calls = self.run_fixture()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['BUILD', 'CREATE', 'PUSH push github HEAD:refs/heads/main', 'EDIT'], calls)

if __name__ == '__main__': unittest.main(verbosity=2)
