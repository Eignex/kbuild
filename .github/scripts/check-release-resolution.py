#!/usr/bin/env python3
"""Resolve release plugins and downstream Maven libraries from separate empty Gradle homes.

By default, serves staged assets over anonymous HTTP using GitHub's exact download layout.
--repository-url verifies an actual public release without forwarding GitHub credentials.
"""
import argparse
from functools import partial
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
import zipfile

from importlib.util import module_from_spec, spec_from_file_location

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
spec = spec_from_file_location("publication_files", Path(__file__).with_name("check-publication-files.py"))
publication_files = module_from_spec(spec)
spec.loader.exec_module(publication_files)


def run_gradle(gradle, project, home, arguments, env):
    # Homes are created by this check, never copied from a developer or CI dependency cache.
    assert not home.exists()
    home.mkdir()
    command = [str(gradle), "-p", str(project), "--gradle-user-home", str(home),
               "--no-daemon", "--console=plain", "--warning-mode=fail", "--stacktrace",
               "-Dorg.gradle.workers.max=2",
               "-Dorg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64,JAVA_HOME_25_X64",
               "-Duser.home=" + str(home.parent / "home"), "-Dorg.gradle.jvmargs=-Xmx2g", *arguments]
    subprocess.run(command, env=env, check=True)


def verify_downstream_publication(producer, version):
    # Check both Gradle metadata and Maven POMs retain the default platform dependency.
    for project, module in (("library", "library"), ("multiplatform", "multiplatform-jvm")):
        # KMP's root metadata redirects JVM consumers to the target publication; implementation
        # dependencies (including the platform) belong to that target's runtime variant.
        base = producer / project / "build/staging-repo/org/example" / module / version
        metadata = json.loads((base / f"{module}-{version}.module").read_text())
        for variant in metadata["variants"]:
            attributes = variant["attributes"]
            if attributes.get("org.gradle.category") == "library" and attributes.get("org.gradle.usage") in {"java-api", "java-runtime"}:
                assert attributes["org.gradle.jvm.version"] == 17, (module, attributes)
        dependencies = [dep for variant in metadata["variants"] for dep in variant.get("dependencies", [])]
        assert any(dep["group"] == "com.eignex" and dep["module"] == "kbuild-platform"
                   and dep["version"]["requires"] == version for dep in dependencies), (module, dependencies)
        assert "<artifactId>kbuild-platform</artifactId>" in (base / f"{module}-{version}.pom").read_text()
    with zipfile.ZipFile(producer / "publish/build/maven-central-publishing/bundle.zip") as bundle:
        assert any(name.endswith(f"publish-{version}.pom") for name in bundle.namelist())


def check(args, work):
    # Invoke the already-installed Gradle directly, so an empty home does not copy a wrapper
    # distribution/cache or need developer credentials. All dependencies are downloaded afresh.
    gradle = Path(args.gradle).resolve()
    env = {key: value for key, value in os.environ.items()
           if key in {"PATH", "JAVA_HOME", "JAVA_HOME_17_X64", "JAVA_HOME_21_X64", "JAVA_HOME_25_X64",
                      "LANG", "LC_ALL", "TMPDIR", "SYSTEMROOT"}}
    # Prevent Gradle/Maven from reading ~/.gradle, ~/.m2, ~/.netrc or local init scripts.
    env["HOME"] = str(work / "home")
    Path(env["HOME"]).mkdir()
    producer = work / "producer"
    consumer = work / "consumer"
    shutil.copytree(ROOT / "samples/release-resolution", producer, ignore=shutil.ignore_patterns(".gradle", "build", ".kotlin"))
    shutil.copytree(ROOT / "samples/release-downstream", consumer, ignore=shutil.ignore_patterns(".gradle", "build", ".kotlin"))

    server = None
    requests = []
    if args.repository_url:
        repository = args.repository_url.rstrip("/")
    else:
        publication_files.check_assets(args.assets, args.version)
        release_dir = work / "http" / f"v{args.version}"
        shutil.copytree(args.assets, release_dir)

        class AnonymousHandler(http.server.SimpleHTTPRequestHandler):
            def do_GET(self):
                assert not self.headers.get("Authorization"), "consumer sent credentials"
                requests.append(self.path)
                super().do_GET()

            def log_message(self, *_):
                pass

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), partial(AnonymousHandler, directory=str(work / "http")))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        repository = f"http://127.0.0.1:{server.server_port}"
    try:
        properties = [f"-PreleaseRepository={repository}", f"-PkbuildVersion={args.version}", f"-PciVersion={args.version}"]
        run_gradle(gradle, producer, work / "producer-gradle-home", [
            *properties, ":library:verifyPlatform", ":library:publishAllPublicationsToLocalStagingRepository",
            ":multiplatform:publishAllPublicationsToLocalStagingRepository", ":cli:runJvm",
            ":publish:publishAllPublicationsToMavenCentralStagingRepository", ":publish:mavenCentralBundle", ":lint:writeEignexDetektConfig",
        ], env)
        verify_downstream_publication(producer, args.version)
        downstream_repo = work / "downstream-maven"
        downstream_repo.mkdir()
        for module in ("library", "multiplatform"):
            shutil.copytree(producer / module / "build/staging-repo", downstream_repo, dirs_exist_ok=True)
        run_gradle(gradle, consumer, work / "consumer-gradle-home", [
            *properties, f"-PdownstreamRepository={downstream_repo.as_uri()}", "run",
        ], env)
        if server:
            expected = {f"/v{args.version}/{module}-{args.version}.module" for module in ("kbuild", "kbuild-platform")}
            assert expected <= set(requests), f"assets were not resolved over HTTP: {requests}"
            assert all(path.startswith(f"/v{args.version}/kbuild-") for path in requests), requests
        print("Verified all five plugin IDs, transitive plugins, platform constraints, downstream Central packaging, and anonymous JVM/KMP consumers")
    finally:
        if server:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--assets", type=Path, default=ROOT / "build/release-assets")
    parser.add_argument("--repository-url", help="public GitHub releases/download base URL")
    parser.add_argument("--gradle", required=True, help="installed Gradle executable (not the wrapper)")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="kbuild-release-resolution-") as directory:
        check(args, Path(directory))
