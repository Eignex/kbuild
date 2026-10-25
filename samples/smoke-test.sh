#!/usr/bin/env bash
# Runs the sample subprojects and asserts positive samples pass + negative samples fail with the expected rule id.
set -uo pipefail

cd "$(dirname "$0")"
GRADLEW="../gradlew"

fail=0

check_warnings() {
    local out="$1"
    if grep -Eqi '(^w:|^warning:|ExperimentalWarning|^Deprecated Gradle features)' <<<"$out"; then
        echo "$out"
        echo "FAIL: build emitted warnings"
        fail=1
    fi
}

run_checked() {
    local out status
    out=$("$@" 2>&1)
    status=$?
    echo "$out"
    check_warnings "$out"
    return "$status"
}

run_positive() {
    local proj="$1"
    echo ">>> positive: $proj"
    if ! run_checked "$GRADLEW" ":$proj:check" --no-daemon; then
        echo "FAIL: expected $proj to pass"
        fail=1
    fi
}

# A targeted positive run: one task rather than the full :check, for a target :check does not
# reach, such as instantiating a wasmWasi module in Wasmtime.
run_task() {
    local proj="$1" task="$2"
    echo ">>> task: $proj:$task"
    if ! run_checked "$GRADLEW" ":$proj:$task" --no-daemon; then
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
    check_warnings "$out"
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
    check_warnings "$out"
}

run_positive jvm-positive
# Exercise the com.eignex.jvm publish path (the mavenJava publication) against local staging.
run_task jvm-positive publishMavenJavaPublicationToLocalStagingRepository
run_positive jvm-multi-release
run_task jvm-multi-release publishJvmPublicationToLocalStagingRepository
run_task jvm-multi-release copyAndroidLibraries
if ! run_checked "$GRADLEW" -p multi-release-consumer runOnJava17 runOnJava25 --no-daemon; then
    echo "FAIL: published multi-release consumers"
    fail=1
fi
if ! run_checked python3 jvm-multi-release/android-smoke.py; then
    echo "FAIL: Android D8/R8 consumers"
    fail=1
fi
run_positive kmp-positive
run_task kmp-positive wasmWasiWasmtimeTest
run_positive cli-positive
run_cli cli-positive runJvm "hello kbuild"
run_cli cli-positive runReleaseExecutableLinuxX64 "hello kbuild"

echo ">>> cli: cli-positive:releaseAssets"
if ! run_checked "$GRADLEW" :cli-positive:releaseAssets --no-daemon --quiet; then
    echo "FAIL: expected cli-positive:releaseAssets to succeed"
    fail=1
elif ! (cd cli-positive/build/release-assets && sha256sum --check --quiet SHA256SUMS); then
    echo "FAIL: release assets missing or checksums wrong"
    fail=1
fi
run_negative jvm-negative-fqn        detektMain UnnecessaryFullyQualifiedName
run_negative jvm-negative-undoc      detektMain UndocumentedPublicClass
run_negative jvm-negative-sentence   detektMain EndOfSentenceFormat
run_negative jvm-negative-deprecated detektMain DeprecatedBlockTag

exit "$fail"
