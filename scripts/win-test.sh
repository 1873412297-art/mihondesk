#!/usr/bin/env bash
# Sync the current working tree (tracked + untracked, minus build output and .git) to the
# Windows test machine over SSH, run the gate there, and pull the test reports back.
#
# Usage: scripts/win-test.sh [gradle args...]
#   default gradle args: spotlessCheck detekt checkFileLength <all module tests>
#
# Nothing here commits, pushes, or touches git on either side. The Windows copy lives in a
# throwaway directory and is overwritten on every run.
set -euo pipefail

HOST=win
REMOTE_DIR='C:/Users/18734/mihon-sync'
REMOTE_DIR_UNIX='/c/Users/18734/mihon-sync'
LOCAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARBALL="$(mktemp -t mihon-sync-XXXX).tar.gz"
trap 'rm -f "$TARBALL"' EXIT

GRADLE_ARGS=("$@")
if [ ${#GRADLE_ARGS[@]} -eq 0 ]; then
    GRADLE_ARGS=(
        spotlessCheck detekt checkFileLength
        :reader-core:test :desktop-webview-host:test
        :desktop-library-data:test :extension-host:test :sync:test :desktop-app:test
    )
fi

echo "==> Packing working tree"
cd "$LOCAL_ROOT"
# Tracked + untracked-but-not-ignored files, plus .git so Android module git calls at
# configure time still work (configure-on-demand means desktop tasks skip the Android
# compile, but Gradle still evaluates app/build.gradle.kts). Build output is dropped.
# COPYFILE_DISABLE stops macOS tar from emitting ._* AppleDouble companions, which
# SQLDelight would otherwise try to parse as .sq sources on the other end.
{ git ls-files; git ls-files -o --exclude-standard; find .git -type f; } \
    | grep -vE '(^|/)build/' \
    | sort -u \
    | while IFS= read -r path; do [ -e "$path" ] && printf '%s\n' "$path"; done \
    | COPYFILE_DISABLE=1 tar -czf "$TARBALL" -T -
echo "    $(du -h "$TARBALL" | cut -f1)"

echo "==> Uploading to $HOST:$REMOTE_DIR"
ssh "$HOST" "powershell -NoProfile -Command \"if (Test-Path '$REMOTE_DIR') { Remove-Item -Recurse -Force '$REMOTE_DIR' }; New-Item -ItemType Directory -Force '$REMOTE_DIR' | Out-Null\""
scp -q "$TARBALL" "$HOST:$REMOTE_DIR/sync.tar.gz"

echo "==> Extracting on $HOST"
ssh "$HOST" "powershell -NoProfile -Command \"cd '$REMOTE_DIR'; tar -xzf sync.tar.gz; Remove-Item sync.tar.gz\""

echo "==> Running gate on $HOST: ${GRADLE_ARGS[*]}"
# --continue so one failing module still runs the rest; the exit code is reported below.
set +e
ssh "$HOST" "powershell -NoProfile -Command \"cd '$REMOTE_DIR'; .\\gradlew.bat ${GRADLE_ARGS[*]} --continue 2>&1 | Select-String -Pattern 'FAILED','tests completed','BUILD','exceeds','> Task .* FAILED'\""
status=$?
set -e

echo "==> Pulling test reports back to build/win-reports"
rm -rf "$LOCAL_ROOT/build/win-reports"
mkdir -p "$LOCAL_ROOT/build/win-reports"
ssh "$HOST" "powershell -NoProfile -Command \"cd '$REMOTE_DIR'; tar -czf reports.tar.gz (Get-ChildItem -Recurse -Directory -Filter test-results | ForEach-Object { Resolve-Path -Relative \$_.FullName })\"" 2>/dev/null || true
scp -q "$HOST:$REMOTE_DIR/reports.tar.gz" "$LOCAL_ROOT/build/win-reports/reports.tar.gz" 2>/dev/null || true
if [ -f "$LOCAL_ROOT/build/win-reports/reports.tar.gz" ]; then
    tar -xzf "$LOCAL_ROOT/build/win-reports/reports.tar.gz" -C "$LOCAL_ROOT/build/win-reports" 2>/dev/null || true
    rm -f "$LOCAL_ROOT/build/win-reports/reports.tar.gz"
fi

echo "==> Windows gate exit status: $status"
exit $status
