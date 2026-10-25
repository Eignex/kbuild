#!/usr/bin/env python3
"""Verify KBuild's combined Central bundle and release plan without uploading."""
import argparse
import hashlib
from pathlib import Path
import re
import sys
import zipfile


EXPECTED = {
    "kbuild": {"jar", "sources", "javadoc", "pom", "module"},
    "kbuild-platform": {"pom", "module"},
    **{f"com.eignex.{plugin}.gradle.plugin": {"pom"} for plugin in ("jvm", "kmp", "cli", "publish", "lint")},
}


def check_bundles(paths):
    files = {}
    for path in paths:
        with zipfile.ZipFile(path) as bundle:
            for entry in bundle.infolist():
                if entry.is_dir():
                    continue
                name = entry.filename
                if name in files:
                    raise ValueError(f"duplicate bundle entry: {name}")
                if name.endswith((".sha256", ".sha512")) or ".asc." in name:
                    raise ValueError(f"unnecessary checksum: {name}")
                files[name] = bundle.read(entry)
    payloads = {name: data for name, data in files.items() if not name.endswith((".asc", ".md5", ".sha1"))}
    slots = set()
    allowed = set()
    signatures = 0
    for name, data in payloads.items():
        parts = name.split("/")
        if len(parts) < 5 or parts[:2] != ["com", "eignex"]:
            raise ValueError(f"unexpected coordinate: {name}")
        artifact = parts[-3]
        kind = next((kind for suffix, kind in (("-sources.jar", "sources"), ("-javadoc.jar", "javadoc"),
                    (".jar", "jar"), (".module", "module"), (".pom", "pom")) if name.endswith(suffix)), None)
        if kind not in EXPECTED.get(artifact, set()) or (artifact, kind) in slots:
            raise ValueError(f"unexpected or duplicate artifact: {name}")
        slots.add((artifact, kind))
        allowed.add(name)
        for algorithm in ("md5", "sha1"):
            checksum = name + "." + algorithm
            if checksum not in files or files[checksum].decode().strip() != hashlib.new(algorithm, data).hexdigest():
                raise ValueError(f"missing or invalid checksum: {checksum}")
            allowed.add(checksum)
        if name + ".asc" in files:
            signatures += 1
            allowed.add(name + ".asc")
    expected_slots = {(artifact, kind) for artifact, kinds in EXPECTED.items() for kind in kinds}
    if slots != expected_slots:
        raise ValueError(f"missing publication artifacts: {sorted(expected_slots - slots)}")
    if signatures not in (0, len(payloads)):
        raise ValueError("publication bundle is only partially signed")
    if set(files) != allowed:
        raise ValueError(f"unexpected bundle files: {sorted(set(files) - allowed)}")
    if len(files) != 36 + signatures:
        raise ValueError(f"unexpected file budget: {len(files)}")
    print(f"Verified {len(payloads)} artifacts, {signatures} signatures, {len(files)} files across 7 publications")


def check_publish_plan(path):
    tasks = re.findall(r"^(:\S+) SKIPPED$", Path(path).read_text(), re.MULTILINE)
    uploads = [task for task in tasks if task.endswith(":uploadMavenCentralBundle")]
    if uploads != [":uploadMavenCentralBundle"]:
        raise ValueError(f"expected one root Central upload, found: {uploads}")
    bundle = ":mavenCentralBundle"
    clean = ":cleanMavenCentralStagingRepository"
    staged = ":kbuild-platform:publishJavaPlatformPublicationToMavenCentralStagingRepository"
    if not all(task in tasks for task in (bundle, clean, staged)):
        raise ValueError("release plan is missing the combined bundle, shared cleanup, or platform staging")
    publications = [task for task in tasks if re.fullmatch(r":(?:kbuild-platform:)?publish\w+PublicationToMavenCentralStagingRepository", task)]
    if len(publications) != 7 or not all(tasks.index(clean) < tasks.index(task) < tasks.index(bundle) for task in publications):
        raise ValueError("all seven publications must stage after cleanup and before bundling")
    if tasks.index(bundle) > tasks.index(uploads[0]):
        raise ValueError("Central upload must follow the combined bundle")
    print("Verified one Central upload after staging all 7 publications")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", help="the combined plugin/platform bundle ZIP")
    parser.add_argument("--publish-plan", help="output of publishToMavenCentral --dry-run --console=plain")
    args = parser.parse_args()
    try:
        check_bundles([args.bundle])
        if args.publish_plan:
            check_publish_plan(args.publish_plan)
    except (ValueError, KeyError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
