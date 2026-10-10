#!/usr/bin/env bash
# Removes ONLY demo records (fictional companies/tender and what involves them) and reseeds them.
# Imported CanadaBuys notices are not touched. Runs against the local backend only.
set -euo pipefail
PORT="${SERVER_PORT:-8090}"
read -r -p "Type RESET DEMO to remove and reseed demo records: " answer
[[ "$answer" == "RESET DEMO" ]] || { echo "Cancelled."; exit 1; }
curl -sS -X POST "http://127.0.0.1:${PORT}/api/demo/reset" \
  -H 'Content-Type: application/json' -d '{"confirm":"RESET DEMO"}'
echo
