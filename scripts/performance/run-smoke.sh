#!/usr/bin/env bash

set -uo pipefail

base_url="${BASE_URL:-https://jhhomehub.gonetis.com/srrrg-dev}"
redirect_requests="${SMOKE_REDIRECT_REQUESTS:-20}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      base_url="$2"
      shift 2
      ;;
    --redirect-requests)
      redirect_requests="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

if ! [[ "$redirect_requests" =~ ^[1-9][0-9]*$ ]]; then
  echo "redirect requests must be a positive integer: $redirect_requests" >&2
  exit 2
fi

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 executable was not found. Install k6 and open a new terminal." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
started_at="$(date '+%Y-%m-%dT%H:%M:%S%z')"
date_path="$(date '+%Y-%m-%d')"
run_name="$(date '+%H%M%S')-smoke-redirect-${redirect_requests}"
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
k6 run \
  -e "BASE_URL=${base_url}" \
  -e "SMOKE_REDIRECT_REQUESTS=${redirect_requests}" \
  "${repo_root}/scripts/performance/smoke.js" 2>&1 |
  tee "$log_path"
exit_code=${PIPESTATUS[0]}
set -e

finished_at="$(date '+%Y-%m-%dT%H:%M:%S%z')"
cat >"$metadata_path" <<EOF
{
  "scenario": "smoke",
  "startedAt": "${started_at}",
  "finishedAt": "${finished_at}",
  "baseUrl": "${base_url}",
  "redirectRequests": ${redirect_requests},
  "commitSha": "${commit_sha}",
  "workingTreeDirty": ${working_tree_dirty},
  "k6Executable": "$(command -v k6)",
  "exitCode": ${exit_code}
}
EOF

echo "Result directory: $result_directory"
exit "$exit_code"
