#!/usr/bin/env python3
"""Dex the published MR-JAR for API 24 and check that desktop APIs are absent."""
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

sample = Path(__file__).resolve().parent
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
jdk = Path(os.environ["JAVA_HOME"])
tools = sdk / "build-tools" / os.environ.get("ANDROID_BUILD_TOOLS", "37.0.0")
android = sdk / "platforms" / os.environ.get("ANDROID_PLATFORM", "android-36") / "android.jar"
module = sample / "build/staging-repo/com/eignex/jvm-multi-release-jvm/1.0-SNAPSHOT"
metadata = ET.parse(module / "maven-metadata.xml")
version = next(entry.findtext("value") for entry in metadata.findall(".//snapshotVersion")
               if entry.findtext("extension") == "jar" and entry.find("classifier") is None)
artifact = module / f"jvm-multi-release-jvm-{version}.jar"
libraries = sorted((sample / "build/android-libraries").glob("*.jar"))
assert artifact.is_file(), "Publish the sample to local staging first"
assert libraries, "Run :jvm-multi-release:copyAndroidLibraries first"
out = sample / "build/android-consumer"
classes = out / "classes"
classes.mkdir(parents=True, exist_ok=True)


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


run(jdk / "bin/javac", "--release", "17", "-cp", artifact, "-d", classes,
    sample / "android/AndroidConsumer.java")
consumer = out / "consumer.jar"
run(jdk / "bin/jar", "--create", "--file", consumer, "-C", classes, ".")
for compiler in ("D8", "R8"):
    destination = out / compiler.lower()
    destination.mkdir(parents=True, exist_ok=True)
    args = [jdk / "bin/java", "-cp", tools / "lib/d8.jar", f"com.android.tools.r8.{compiler}",
            "--release", "--min-api", "24", "--lib", android, "--output", destination]
    for library in libraries:
        args.extend(["--lib", library])
    if compiler == "R8":
        args.extend(["--pg-conf", sample / "android/keep.pro"])
    run(*args, artifact, consumer)
    dex_files = list(destination.glob("*.dex"))
    assert dex_files, f"{compiler} produced no dex"
    dex = b"".join(path.read_bytes() for path in dex_files)
    assert b"Lconsumer/AndroidConsumer;" in dex, f"{compiler} dropped the consumer"
    assert b"portable" in dex, f"{compiler} lost the portable implementation"
    for forbidden in (b"java/lang/foreign", b"jdk/incubator/vector", b"ffm"):
        assert forbidden not in dex, f"{compiler} retained a desktop reference: {forbidden!r}"
    print(f"{compiler}: API 24 consumer retained portable code and excluded desktop code")
