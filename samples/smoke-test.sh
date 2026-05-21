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
    local proj="$1" expected_rule="$2"
    echo ">>> negative: $proj (expecting $expected_rule)"
    local out
    if out=$("$GRADLEW" ":$proj:detektMain" --no-daemon 2>&1); then
        echo "$out"
        echo "FAIL: expected $proj:detektMain to fail"
        fail=1
    elif ! grep -q "\[$expected_rule\]" <<<"$out"; then
        echo "$out"
        echo "FAIL: $proj failed but did not report [$expected_rule]"
        fail=1
    fi
}

run_positive jvm-positive
run_negative jvm-negative-fqn UnnecessaryFullyQualifiedName

exit "$fail"
