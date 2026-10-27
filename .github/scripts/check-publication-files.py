#!/usr/bin/env python3
"""Verify the exact flat GitHub Release layout and unmodified Gradle metadata."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


PLUGIN_DEPENDENCIES = {
    "asm", "kotlin-gradle-plugin", "dokka-gradle-plugin", "kover-gradle-plugin",
    "gradle-maven-central-publishing", "detekt-gradle-plugin",
}
PLATFORM_BOMS = {"kotlinx-coroutines-bom", "kotlinx-serialization-bom"}
PLATFORM_CONSTRAINTS = {"junit-jupiter", "junit-platform-launcher"}


def check_assets(directory, version):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?", version) or "SNAPSHOT" in version:
        raise ValueError(f"expected a pinned release version, got: {version}")
    expected = {
        f"kbuild-{version}{suffix}" for suffix in
        (".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module")
    } | {f"kbuild-platform-{version}{suffix}" for suffix in (".pom", ".module")}
    actual = {file.name for file in directory.iterdir()}
    if actual != expected | {"SHA256SUMS"}:
        raise ValueError(f"unexpected asset layout; missing={expected - actual}, extra={actual - expected - {'SHA256SUMS'}}")
    checksums = {}
    for line in (directory / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split("  ", 1)
        if name in checksums or name not in expected:
            raise ValueError(f"unexpected or duplicate checksum: {name}")
        checksums[name] = digest
    if set(checksums) != expected:
        raise ValueError("SHA256SUMS must cover every release asset exactly once")
    for name, digest in checksums.items():
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != digest:
            raise ValueError(f"invalid SHA-256 checksum: {name}")

    for module in ("kbuild", "kbuild-platform"):
        metadata = json.loads((directory / f"{module}-{version}.module").read_text())
        component = metadata["component"]
        if (component["group"], component["module"], component["version"]) != ("com.eignex", module, version):
            raise ValueError(f"inconsistent module coordinates: {component}")
        variants = {variant["name"]: variant for variant in metadata["variants"]}
        for name, usage in (("apiElements", "java-api"), ("runtimeElements", "java-runtime")):
            variant = variants[name]
            attributes = variant["attributes"]
            category = "platform" if module == "kbuild-platform" else "library"
            if attributes["org.gradle.category"] != category or attributes["org.gradle.usage"] != usage:
                raise ValueError(f"lost variant attributes: {module}/{name}")
            dependencies = {dependency["module"] for dependency in variant.get("dependencies", [])}
            if module == "kbuild" and name == "runtimeElements" and not PLUGIN_DEPENDENCIES <= dependencies:
                raise ValueError(f"lost transitive plugin dependencies: {PLUGIN_DEPENDENCIES - dependencies}")
            if module == "kbuild-platform":
                constraints = {constraint["module"] for constraint in variant["dependencyConstraints"]}
                if not PLATFORM_BOMS <= dependencies or not PLATFORM_CONSTRAINTS <= constraints:
                    raise ValueError(f"lost platform imports or constraints: {name}")
        for variant in metadata["variants"]:
            for artifact in variant.get("files", []):
                name = artifact["url"]
                if name not in expected or artifact["name"] != name:
                    raise ValueError(f"metadata URL does not resolve in the flat release: {name}")
                data = (directory / name).read_bytes()
                if artifact["size"] != len(data):
                    raise ValueError(f"invalid metadata size: {name}")
                for algorithm in ("sha512", "sha256", "sha1", "md5"):
                    if artifact[algorithm] != hashlib.new(algorithm, data).hexdigest():
                        raise ValueError(f"invalid metadata {algorithm}: {name}")
        pom = ET.parse(directory / f"{module}-{version}.pom").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        if [pom.findtext(f"m:{field}", namespaces=ns) for field in ("groupId", "artifactId", "version")] != ["com.eignex", module, version]:
            raise ValueError(f"inconsistent POM coordinates: {module}")
    print(f"Verified {len(expected)} versioned release assets, checksums, plugin variants/dependencies, and platform constraints")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="flat release-assets directory")
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    try:
        check_assets(args.directory, args.version)
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        sys.exit(str(error))
