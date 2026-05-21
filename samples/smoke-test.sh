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

run_positive jvm-positive
run_negative jvm-negative-fqn        detektMain UnnecessaryFullyQualifiedName
run_negative jvm-negative-undoc      detekt     UndocumentedPublicClass
run_negative jvm-negative-sentence   detekt     EndOfSentenceFormat
run_negative jvm-negative-deprecated detekt     DeprecatedBlockTag

exit "$fail"
