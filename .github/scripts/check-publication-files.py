#!/usr/bin/env python3
"""Verify KBuild's Central bundles without uploading or changing their contents."""
import hashlib
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


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3:
            raise ValueError("expected the plugin and platform bundle ZIP paths")
        check_bundles(sys.argv[1:])
    except (ValueError, KeyError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
