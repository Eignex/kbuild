#!/usr/bin/env bash
# Verify the flat GitHub Release layout and Gradle's generated metadata.
set -euo pipefail

fail() { echo "ERROR: $*" >&2; exit 1; }
[[ $# == 3 && $2 == --version ]] || fail "usage: $0 ASSET_DIRECTORY --version VERSION"
directory=$(cd "$1" && pwd -P)
version=$3
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ && $version != *SNAPSHOT* ]] || fail "expected a pinned release version: $version"
for command in jq sha256sum sha512sum sha1sum md5sum; do
    command -v "$command" >/dev/null || fail "required command is missing: $command"
done
expected=("kbuild-$version.jar" "kbuild-$version-sources.jar" "kbuild-$version-javadoc.jar"
          "kbuild-$version.pom" "kbuild-$version.module"
          "kbuild-platform-$version.pom" "kbuild-platform-$version.module")
diff -u <(printf '%s\n' "${expected[@]}" SHA256SUMS | sort) \
    <(find "$directory" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort) || fail "unexpected release asset layout"
for name in "${expected[@]}" SHA256SUMS; do
    [[ -f $directory/$name ]] || fail "asset is not a regular file: $name"
done
manifest_names=()
while IFS= read -r line; do
    [[ $line =~ ^[a-f0-9]{64}'  '(.+)$ ]] || fail "invalid SHA256SUMS entry: $line"
    manifest_names+=("${BASH_REMATCH[1]}")
done < "$directory/SHA256SUMS"
diff -u <(printf '%s\n' "${expected[@]}" | sort) \
    <(printf '%s\n' "${manifest_names[@]}" | sort) || fail "SHA256SUMS must cover every asset exactly once"
(cd "$directory" && sha256sum --strict --check SHA256SUMS)

for module in kbuild kbuild-platform; do
    metadata=$directory/$module-$version.module
    jq -e --arg module "$module" --arg version "$version" '
        .component.group == "com.eignex" and .component.module == $module and .component.version == $version
    ' "$metadata" >/dev/null || fail "inconsistent module coordinates: $module"
    category=library
    [[ $module != kbuild-platform ]] || category=platform
    for variant_name in apiElements runtimeElements; do
        usage=java-api
        [[ $variant_name != runtimeElements ]] || usage=java-runtime
        variant=$(jq -ce --arg name "$variant_name" 'first(.variants[] | select(.name == $name))' "$metadata")
        jq -e --arg category "$category" --arg usage "$usage" '
            .attributes["org.gradle.category"] == $category and .attributes["org.gradle.usage"] == $usage
        ' <<< "$variant" >/dev/null || fail "lost variant attributes: $module/$variant_name"
        if [[ $module == kbuild && $variant_name == runtimeElements ]]; then
            jq -e '
                (["asm", "kotlin-gradle-plugin", "dokka-gradle-plugin", "kover-gradle-plugin",
                  "gradle-maven-central-publishing", "detekt-gradle-plugin"] - [.dependencies[]?.module]) == []
            ' <<< "$variant" >/dev/null || fail "lost transitive plugin dependencies"
        elif [[ $module == kbuild-platform ]]; then
            jq -e '
                (["kotlinx-coroutines-bom", "kotlinx-serialization-bom"] - [.dependencies[]?.module]) == [] and
                (["junit-jupiter", "junit-platform-launcher"] - [.dependencyConstraints[]?.module]) == []
            ' <<< "$variant" >/dev/null || fail "lost platform imports or constraints: $variant_name"
        fi
    done
    while IFS=$'\t' read -r name url size sha512 sha256 sha1 md5; do
        [[ $name == "$url" && " ${expected[*]} " == *" $url "* ]] || fail "invalid metadata artifact URL: $url"
        file=$directory/$url
        [[ $size =~ ^[0-9]+$ && $size -eq $(wc -c < "$file") ]] || fail "invalid metadata size: $url"
        hashes=("$sha512" "$sha256" "$sha1" "$md5")
        algorithms=(sha512 sha256 sha1 md5)
        for index in "${!algorithms[@]}"; do
            algorithm=${algorithms[$index]}
            actual=$("${algorithm}sum" "$file")
            [[ ${actual%% *} == "${hashes[$index]}" ]] || fail "invalid metadata $algorithm: $url"
        done
    done < <(jq -r '.variants[].files[]? | [.name, .url, .size, .sha512, .sha256, .sha1, .md5] | @tsv' "$metadata")
    # These POMs are Gradle-generated: project coordinates occupy the unindented root fields.
    for field in groupId artifactId version; do
        expected_value=com.eignex
        [[ $field != artifactId ]] || expected_value=$module
        [[ $field != version ]] || expected_value=$version
        actual=$(awk -v tag="$field" '$0 ~ "^  <" tag ">" { sub("^  <" tag ">", ""); sub("</" tag ">.*$", ""); print; exit }' "$directory/$module-$version.pom")
        [[ $actual == "$expected_value" ]] || fail "inconsistent POM $field: $module"
    done
done
printf '%s\n' 'Verified release assets, checksums, plugin variants/dependencies, and platform constraints'
