#!/usr/bin/env bash
# Resolve plugins and published downstream libraries using separate empty Gradle homes.
# JDK 25's jwebserver serves the exact anonymous GitHub download layout locally.
set -euo pipefail

fail() { echo "ERROR: $*" >&2; exit 1; }
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
assets=$root/build/release-assets
version='' repository='' gradle=''
while (($#)); do
    [[ $# -ge 2 ]] || fail "missing value for $1"
    case $1 in
        --version) version=$2 ;;
        --assets) assets=$2 ;;
        --repository-url) repository=${2%/} ;;
        --gradle) gradle=$2 ;;
        *) fail "unknown argument: $1" ;;
    esac
    shift 2
done
[[ -n $version && -n $gradle ]] || fail "usage: $0 --version VERSION --gradle EXECUTABLE [--assets DIRECTORY | --repository-url URL]"
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ && $version != *SNAPSHOT* ]] || fail "expected a pinned release version: $version"
gradle=$(realpath "$gradle")
[[ -x $gradle ]] || fail "Gradle executable is missing: $gradle"
work=$(mktemp -d "${TMPDIR:-/tmp}/kbuild-release-resolution.XXXXXXXX")
server_pid=
cleanup() {
    if [[ -n $server_pid ]]; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -r -- "$work"
}
trap cleanup EXIT
mkdir -p "$work/home" "$work/producer" "$work/consumer" "$work/downstream-maven"
for fixture in release-resolution release-downstream; do
    destination=$work/producer
    [[ $fixture != release-downstream ]] || destination=$work/consumer
    tar --exclude=.gradle --exclude=build --exclude=.kotlin -C "$root/samples/$fixture" -cf - . | tar -C "$destination" -xf -
done

# No GitHub tokens, Gradle project properties, Maven settings, init scripts or caches
# are inherited. Only installed JDK paths and process essentials reach these builds.
clean_env=(env -i "HOME=$work/home")
for name in PATH JAVA_HOME JAVA_HOME_17_X64 JAVA_HOME_21_X64 JAVA_HOME_25_X64 LANG LC_ALL TMPDIR SYSTEMROOT; do
    if [[ -v $name ]]; then clean_env+=("$name=${!name}"); fi
done
server_log=$work/http.log
if [[ -z $repository ]]; then
    bash "$root/.github/scripts/check-publication-files.sh" "$assets" --version "$version"
    mkdir -p "$work/http/v$version"
    cp "$assets"/* "$work/http/v$version/"
    [[ -x ${JAVA_HOME:-}/bin/jwebserver ]] || fail "JAVA_HOME must point to JDK 25 (jwebserver is required)"
    "${clean_env[@]}" "$JAVA_HOME/bin/jwebserver" -b 127.0.0.1 -p 0 -d "$work/http" > "$server_log" 2>&1 &
    server_pid=$!
    for ((attempt=0; attempt<100; attempt++)); do
        repository=$(sed -n 's/^URL \(http:\/\/.*\)$/\1/p' "$server_log")
        if [[ -n $repository ]]; then break; fi
        kill -0 "$server_pid" 2>/dev/null || { cat "$server_log" >&2; fail "jwebserver failed to start"; }
        sleep 0.1
    done
    [[ -n $repository ]] || fail "timed out starting jwebserver"
    repository=${repository%/}
fi
properties=("-PreleaseRepository=$repository" "-PkbuildVersion=$version" "-PciVersion=$version")
run_gradle() {
    local project=$1 home=$2
    shift 2
    [[ ! -e $home ]] || fail "Gradle user home must start empty: $home"
    mkdir "$home"
    "${clean_env[@]}" "$gradle" -p "$project" --gradle-user-home "$home" \
        --no-daemon --console=plain --warning-mode=fail --stacktrace \
        -Dorg.gradle.workers.max=2 '-Dorg.gradle.jvmargs=-Xmx2g' "-Duser.home=$work/home" \
        -Dorg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64,JAVA_HOME_25_X64 \
        "${properties[@]}" "$@"
}
run_gradle "$work/producer" "$work/producer-gradle-home" \
    :library:verifyPlatform :library:publishAllPublicationsToLocalStagingRepository \
    :multiplatform:publishAllPublicationsToLocalStagingRepository :cli:runJvm \
    :publish:publishAllPublicationsToMavenCentralStagingRepository :publish:mavenCentralBundle \
    :lint:writeEignexDetektConfig

for project in library multiplatform; do
    module=$project
    [[ $project != multiplatform ]] || module=multiplatform-jvm
    # KMP implementation dependencies belong to the JVM target, reached via root metadata.
    base=$work/producer/$project/build/staging-repo/org/example/$module/$version
    jq -e --arg version "$version" '
        all(.variants[] | select(.attributes["org.gradle.category"] == "library" and
            (.attributes["org.gradle.usage"] == "java-api" or .attributes["org.gradle.usage"] == "java-runtime"));
            .attributes["org.gradle.jvm.version"] == 17) and
        any(.variants[].dependencies[]?; .group == "com.eignex" and .module == "kbuild-platform" and .version.requires == $version)
    ' "$base/$module-$version.module" >/dev/null || fail "lost Java 17 attributes or platform dependency: $module"
    grep -Fq '<artifactId>kbuild-platform</artifactId>' "$base/$module-$version.pom" || fail "lost POM platform import: $module"
    cp -R "$work/producer/$project/build/staging-repo/." "$work/downstream-maven/"
done
bundle_contents=$(unzip -Z1 "$work/producer/publish/build/maven-central-publishing/bundle.zip")
grep -Fq "publish-$version.pom" <<< "$bundle_contents" || fail "downstream Central bundle is missing its POM"
run_gradle "$work/consumer" "$work/consumer-gradle-home" "-PdownstreamRepository=$work/downstream-maven" run
if [[ -n $server_pid ]]; then
    for module in kbuild kbuild-platform; do
        grep -Fq "\"GET /v$version/$module-$version.module " "$server_log" || fail "module was not fetched over HTTP: $module"
    done
    requests=$(sed -n 's/.*"\(GET\|HEAD\) \([^ ]*\) HTTP\/.*/\2/p' "$server_log")
    while IFS= read -r path; do
        [[ $path == "/v$version/kbuild-"* ]] || fail "unrelated coordinate requested from release server: $path"
    done <<< "$requests"
fi
printf '%s\n' 'Verified all five plugin IDs, transitive plugins, Java 17 attributes, platform constraints, downstream Central packaging, and anonymous JVM/KMP consumers'
