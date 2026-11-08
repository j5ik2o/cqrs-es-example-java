#!/usr/bin/env bash
#
# Builds the Lambda deployment package for the RMU, recovery worker, and resync
# worker handlers. Pass --upload to copy the ZIP to S3 after the build.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT="${SCRIPT_DIR}/../rmu/build/distributions/rmu-lambda.zip"
UPLOAD="false"
SKIP_BUILD="false"
BUCKET=""
KEY="rmu/rmu-lambda.zip"

usage() {
  cat <<'USAGE'
Usage: build-lambda-package.sh [options]

Options:
  --upload                Upload the ZIP to --bucket after building it.
  --bucket <name>         S3 bucket for --upload.
  --key <key>             S3 key. Defaults to rmu/rmu-lambda.zip.
  --output <path>         Local ZIP path. Defaults to rmu/build/distributions/rmu-lambda.zip.
  --skip-build            Reuse an existing ZIP instead of running Gradle.
  -h, --help              Show this help.

Build with JDK 17 or 21. The ZIP contains the handler classes at its root and
the dependency JARs in lib/, which is the layout the AWS Lambda Java runtime
expects.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --upload)
      UPLOAD="true"
      shift
      ;;
    --bucket)
      BUCKET="$2"
      shift 2
      ;;
    --key)
      KEY="$2"
      shift 2
      ;;
    --output)
      OUTPUT="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD="true"
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$UPLOAD" == "true" && -z "$BUCKET" ]]; then
  echo "--bucket is required with --upload" >&2
  exit 2
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  "${REPO_ROOT}/gradlew" -p "${REPO_ROOT}" :rmu:lambdaZip
fi

if [[ ! -f "$OUTPUT" ]]; then
  echo "lambda package not found: $OUTPUT" >&2
  exit 2
fi

echo "lambda package: $OUTPUT"
echo "size: $(wc -c <"$OUTPUT" | tr -d ' ') bytes"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUTPUT"
else
  shasum -a 256 "$OUTPUT"
fi

if [[ "$UPLOAD" != "true" ]]; then
  echo "not uploaded: re-run with --upload --bucket <name> [--key <key>] to copy it to S3." >&2
  exit 0
fi

command -v aws >/dev/null 2>&1 || {
  echo "the AWS CLI is required for --upload" >&2
  exit 2
}

aws s3 cp "$OUTPUT" "s3://${BUCKET}/${KEY}"
echo "uploaded: s3://${BUCKET}/${KEY}"
