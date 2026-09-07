#!/usr/bin/env bash
#
# Warm a Render backend, then deploy it.
#
# The order is the whole point. docs/operations/render-free-tier.md records three deploys with an
# identical, fully cached Docker build: 24 s when an instance was already running, 24 s when the
# service had been warmed deliberately, and 13 min 14 s — timing out against Render's fixed
# 15-minute budget — when it had been asleep for five and a half hours. On that run Tomcat
# initialised four seconds before Render gave up.
#
# A timed-out backend deploy is worse than a failed one, because the frontend static site deploys
# independently and succeeds: the app then serves new JavaScript against the old API, and nothing
# announces it. So this script also reports both services at the end rather than the one you were
# watching.
#
# Usage:
#   ops/render/warm-and-deploy.sh                 # the dev environment, from main
#   ops/render/warm-and-deploy.sh staging         # the staging pair, from the staging branch
#
# Requires the Render CLI, logged in: https://render.com/docs/cli

set -euo pipefail

ENVIRONMENT="${1:-dev}"
case "$ENVIRONMENT" in
  dev)     API_NAME=chalkbase-api;         WEB_NAME=chalkbase-web ;;
  staging) API_NAME=chalkbase-api-staging; WEB_NAME=chalkbase-web-staging ;;
  *) echo "Usage: $0 [dev|staging]" >&2; exit 2 ;;
esac

command -v render >/dev/null || { echo 'The Render CLI is not on PATH.' >&2; exit 2; }

service_field() {  # name, jq-ish field
  render services list -o json --confirm 2>/dev/null | python3 -c "
import json, sys
name, field = sys.argv[1], sys.argv[2]
for row in json.load(sys.stdin):
    s = row['service']
    if s['name'] == name:
        print(s[field] if field in s else s['serviceDetails'].get(field, ''))
        break
" "$1" "$2"
}

API_ID=$(service_field "$API_NAME" id)
API_URL=$(service_field "$API_NAME" url)
WEB_ID=$(service_field "$WEB_NAME" id)

[ -n "$API_ID" ] || { echo "No Render service named $API_NAME. Has the Blueprint been synced?" >&2; exit 1; }

echo "Warming $API_NAME ($API_URL)"
echo 'A free instance that has been idle answers its first request in 86 to 121 seconds — it wakes,'
echo 'then runs a per-tenant Flyway pass. That wait is the tier, not a fault.'
echo

# Twenty attempts at up to 180 s each is generous on purpose: the point is to stop only when the
# service is genuinely warm, because giving up early and deploying anyway is the failure this
# script exists to prevent.
for attempt in $(seq 1 20); do
  code_time=$(curl -s -o /dev/null -m 180 -w '%{http_code} %{time_total}' "$API_URL/actuator/health" || echo '000 timeout')
  code=${code_time%% *}
  elapsed=${code_time##* }
  printf '  attempt %2d: %s in %ss\n' "$attempt" "$code" "$elapsed"

  # Warm means a fast 200, not just a 200. The first 200 after a sleep took 121 s and the instance
  # is still settling behind it.
  if [ "$code" = '200' ] && [ "${elapsed%%.*}" -lt 5 ]; then
    echo '  warm.'
    break
  fi
  [ "$attempt" -eq 20 ] && { echo 'Never went warm. Do not deploy — investigate first.' >&2; exit 1; }
done

echo
echo "Deploying $API_NAME"
render deploys create "$API_ID" --output json --confirm --wait

echo
echo 'Both services, so a silent version skew cannot hide:'
render deploys list "$API_ID" --output text --confirm -- --limit 1 2>/dev/null || true
render deploys list "$WEB_ID" --output text --confirm -- --limit 1 2>/dev/null || true
