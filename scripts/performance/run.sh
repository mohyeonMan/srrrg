#!/usr/bin/env bash

set -uo pipefail

scenario="${1:-}"
if ! [[ "$scenario" =~ ^[a-z0-9-]+$ ]]; then
  echo "Usage: bash scripts/performance/run.sh <scenario>" >&2
  exit 2
fi

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 executable was not found. Install k6 and open a new terminal." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script_path="${repo_root}/scripts/performance/${scenario}.js"
if [[ ! -f "$script_path" ]]; then
  echo "Performance script was not found: $script_path" >&2
  exit 2
fi

started_at="$(date '+%Y-%m-%dT%H:%M:%S%z')"
date_path="$(date '+%Y-%m-%d')"
run_name="$(date '+%H%M%S')-${scenario}"
result_directory="${repo_root}/docs/performance/results/${date_path}/${run_name}"
log_path="${result_directory}/k6-output.log"
metadata_path="${result_directory}/metadata.json"

mkdir -p "$result_directory"

commit_sha="$(git -C "$repo_root" rev-parse HEAD)"
if [[ -n "$(git -C "$repo_root" status --porcelain)" ]]; then
  working_tree_dirty=true
else
  working_tree_dirty=false
fi

set +e
k6 run "$script_path" 2>&1 | tee "$log_path"
exit_code=${PIPESTATUS[0]}
set -e

finished_at="$(date '+%Y-%m-%dT%H:%M:%S%z')"
cat >"$metadata_path" <<EOF
{
  "scenario": "${scenario}",
  "startedAt": "${started_at}",
  "finishedAt": "${finished_at}",
  "script": "${script_path}",
  "commitSha": "${commit_sha}",
  "workingTreeDirty": ${working_tree_dirty},
  "k6Executable": "$(command -v k6)",
  "exitCode": ${exit_code},
  "analysisStatus": "pending"
}
EOF

echo "Result directory: $result_directory"
exit "$exit_code"
