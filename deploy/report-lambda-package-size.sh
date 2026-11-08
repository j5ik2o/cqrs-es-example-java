#!/usr/bin/env bash
#
# Reports the size breakdown of the Lambda deployment package: the compressed
# ZIP size, the uncompressed size, and the largest dependency JARs under lib/.
# It only writes to stdout and never fails on size, so CI can log the numbers
# without imposing a pass/fail threshold.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ZIP="${1:-${REPO_ROOT}/rmu/build/distributions/rmu-lambda.zip}"
TOP="${TOP:-20}"

usage() {
  cat <<'USAGE'
Usage: report-lambda-package-size.sh [zip]

Prints the compressed and uncompressed size of the Lambda package, the number
of files, the size of lib/, and the largest dependency JARs under lib/.

Environment:
  TOP   Non-negative number of JARs to list. Defaults to 20.
USAGE
}

case "$ZIP" in
  -h | --help)
    usage
    exit 0
    ;;
esac

if [[ ! -f "$ZIP" ]]; then
  echo "lambda package not found: $ZIP" >&2
  exit 2
fi

if ! [[ "$TOP" =~ ^[0-9]+$ ]]; then
  echo "TOP must be a non-negative integer: $TOP" >&2
  exit 2
fi

python3 -I - "$ZIP" "$TOP" <<'PY'
import os
import sys
import zipfile

path = sys.argv[1]
top = int(sys.argv[2])


def mib(size):
    return size / 1024 / 1024


def commas(size):
    return f"{size:,}"


with zipfile.ZipFile(path) as archive:
    entries = [info for info in archive.infolist() if not info.is_dir()]

jars = [
    info
    for info in entries
    if info.filename.startswith("lib/") and info.filename.endswith(".jar")
]
uncompressed = sum(info.file_size for info in entries)
lib_entries = [info for info in entries if info.filename.startswith("lib/")]
lib_bytes = sum(info.file_size for info in lib_entries)

print(f"Lambda package: {path}")
print(f"compressed:   {commas(os.path.getsize(path))} bytes ({mib(os.path.getsize(path)):.2f} MiB)")
print(f"uncompressed: {commas(uncompressed)} bytes ({mib(uncompressed):.2f} MiB)")
print(f"files:        {len(entries)} ({len(jars)} dependency JARs in lib/)")
print(f"lib/:         {commas(lib_bytes)} bytes ({mib(lib_bytes):.2f} MiB)")
print()
print(f"Top {top} JARs in lib/ by uncompressed size:")
for rank, info in enumerate(sorted(jars, key=lambda i: i.file_size, reverse=True)[:top], start=1):
    name = info.filename[len("lib/"):]
    print(f"{rank:>3}. {commas(info.file_size):>12} B ({mib(info.file_size):>6.2f} MiB)  {name}")

# Matched by artifact name. docs/LAMBDA_PACKAGE_SIZE.md confirms with
# `dependencyInsight` that event-store-adapter-java pulls these in.
BACKENDS = {
    "sqlite": ("sqlite-jdbc-",),
    "spanner": (
        "google-cloud-spanner",
        "proto-google-cloud-spanner",
        "grpc-google-cloud-spanner",
    ),
    "bigtable": (
        "google-cloud-bigtable",
        "proto-google-cloud-bigtable",
        "grpc-google-cloud-bigtable",
    ),
}

print()
print("Event store adapter backends in lib/ (filename match):")
for backend, prefixes in BACKENDS.items():
    matches = sorted(
        (
            info
            for info in jars
            if os.path.basename(info.filename).startswith(prefixes)
        ),
        key=lambda i: i.file_size,
        reverse=True,
    )
    total = sum(info.file_size for info in matches)
    print(f"  {backend:<9} {len(matches):>2} JAR(s), {commas(total)} bytes ({mib(total):.2f} MiB)")
    for info in matches:
        print(f"             {commas(info.file_size):>12} B  {info.filename[len('lib/'):]}")
PY
