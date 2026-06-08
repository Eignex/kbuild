#!/usr/bin/env bash
# Runs the sample subprojects and asserts positive samples pass + negative samples fail with the expected rule id.
set -uo pipefail

cd "$(dirname "$0")"
GRADLEW="../gradlew"

fail=0

run_positive() {
    local proj="$1"
    echo ">>> positive: $proj"
    if ! "$GRADLEW" ":$proj:check" --no-daemon; then
        echo "FAIL: expected $proj to pass"
        fail=1
    fi
}

# A targeted positive run — one task rather than the full :check. Used by the kmp sample,
# whose only job is to instantiate a wasmWasi module on the pinned node.
run_task() {
    local proj="$1" task="$2"
    echo ">>> task: $proj:$task"
    if ! "$GRADLEW" ":$proj:$task" --no-daemon; then
        echo "FAIL: expected $proj:$task to pass"
        fail=1
    fi
}

run_negative() {
    local proj="$1" task="$2" expected_rule="$3"
    echo ">>> negative: $proj:$task (expecting $expected_rule)"
    local out
    if out=$("$GRADLEW" ":$proj:$task" --no-daemon 2>&1); then
        echo "$out"
        echo "FAIL: expected $proj:$task to fail"
        fail=1
    elif ! grep -q "\[$expected_rule\]" <<<"$out"; then
        echo "$out"
        echo "FAIL: $proj failed but did not report [$expected_rule]"
        fail=1
    fi
}

run_cli() {
    local proj="$1" task="$2" expected="$3"
    echo ">>> cli: $proj:$task (expecting \"$expected\")"
    local out
    if ! out=$("$GRADLEW" ":$proj:$task" --no-daemon --quiet 2>&1); then
        echo "$out"
        echo "FAIL: expected $proj:$task to succeed"
        fail=1
    elif ! grep -q "$expected" <<<"$out"; then
        echo "$out"
        echo "FAIL: $proj:$task did not print \"$expected\""
        fail=1
    fi
}

run_positive jvm-positive
run_task kmp-positive wasmWasiNodeTest
run_positive cli-positive
run_cli cli-positive runJvm "hello kbuild"
run_cli cli-positive runReleaseExecutableLinuxX64 "hello kbuild"

echo ">>> cli: cli-positive:releaseAssets"
if ! "$GRADLEW" :cli-positive:releaseAssets --no-daemon --quiet; then
    echo "FAIL: expected cli-positive:releaseAssets to succeed"
    fail=1
elif ! (cd cli-positive/build/release-assets && sha256sum --check --quiet SHA256SUMS); then
    echo "FAIL: release assets missing or checksums wrong"
    fail=1
fi
run_negative jvm-negative-fqn        detektMain UnnecessaryFullyQualifiedName
run_negative jvm-negative-undoc      detekt     UndocumentedPublicClass
run_negative jvm-negative-sentence   detekt     EndOfSentenceFormat
run_negative jvm-negative-deprecated detekt     DeprecatedBlockTag

exit "$fail"
