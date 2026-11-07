#!/usr/bin/env bash
#
# Applies the RMU deployment template. The default mode only validates the
# template; pass --apply to create or update the CloudFormation stack.
set -euo pipefail

STACK_NAME="${RMU_STACK_NAME:-cqrs-es-example-rmu}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE="${SCRIPT_DIR}/rmu-deployment.template.json"
PARAMETERS_FILE=""
MODE="dry-run"

usage() {
  cat <<'USAGE'
Usage: apply-rmu-deployment.sh [options]

Options:
  --apply                 Create or update the CloudFormation stack. Without
                          this flag the script only validates the template.
  --stack-name <name>     Stack name. Defaults to $RMU_STACK_NAME or
                          cqrs-es-example-rmu.
  --parameters <file>     JSON file of CloudFormation parameter values. See
                          parameters.example.json.
  -h, --help              Show this help.

The AWS CLI must be configured with credentials for the target account.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)
      MODE="apply"
      shift
      ;;
    --stack-name)
      STACK_NAME="$2"
      shift 2
      ;;
    --parameters)
      PARAMETERS_FILE="$2"
      shift 2
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

if [[ -n "$PARAMETERS_FILE" && ! -f "$PARAMETERS_FILE" ]]; then
  echo "parameter file not found: $PARAMETERS_FILE" >&2
  exit 2
fi

if command -v jq >/dev/null 2>&1; then
  jq empty "$TEMPLATE"
else
  python3 -m json.tool "$TEMPLATE" >/dev/null
fi
echo "template syntax is valid: $TEMPLATE"

if [[ "$MODE" != "apply" ]]; then
  echo "dry run: re-run with --apply to deploy the stack '$STACK_NAME'." >&2
  exit 0
fi

command -v aws >/dev/null 2>&1 || {
  echo "the AWS CLI is required for --apply" >&2
  exit 2
}

aws cloudformation validate-template --template-body "file://${TEMPLATE}" >/dev/null

args=(
  deploy
  --template-file "$TEMPLATE"
  --stack-name "$STACK_NAME"
  --capabilities CAPABILITY_IAM
)

if [[ -n "$PARAMETERS_FILE" ]]; then
  overrides=()
  while IFS= read -r line; do
    overrides+=("$line")
  done < <(
    if command -v jq >/dev/null 2>&1; then
      jq -r 'to_entries[] | select(.value != "") | "\(.key)=\(.value)"' "$PARAMETERS_FILE"
    else
      python3 -I -c '
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    parameters = json.load(handle)
for key, value in parameters.items():
    if value != "":
        print(f"{key}={value}")
' "$PARAMETERS_FILE"
    fi
  )
  args+=(--parameter-overrides "${overrides[@]}")
fi

aws cloudformation "${args[@]}"
