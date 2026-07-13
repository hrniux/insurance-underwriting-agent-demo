#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

health_file="$TEMP_DIR/health.json"
healthy=false
for attempt in $(seq 1 30); do
  if curl --silent --show-error --fail "$BASE_URL/actuator/health" >"$health_file" 2>/dev/null; then
    healthy=true
    break
  fi
  sleep 1
done

if [[ "$healthy" != "true" ]]; then
  echo "Service did not become healthy within 30 seconds: $BASE_URL" >&2
  exit 1
fi

echo "[1/5] Health"
python3 -m json.tool "$health_file"

echo "[2/5] RAG knowledge search"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/knowledge/search" \
  -H 'Content-Type: application/json' \
  -d '{"query":"仓库暴雨红色风险如何核保","topK":4,"productCode":"PROPERTY"}' \
  >"$TEMP_DIR/search.json"
python3 -m json.tool "$TEMP_DIR/search.json"

echo "[3/5] Shared business tool"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/tools/GET_POLICY/invoke" \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-1001"}' \
  >"$TEMP_DIR/tool.json"
python3 -m json.tool "$TEMP_DIR/tool.json"

echo "[4/5] High-risk warehouse evaluation (P-1001)"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: demo-p1001' \
  -d '{"policyNo":"P-1001","question":"暴雨风险较高，这张仓库财产险能否承保？"}' \
  >"$TEMP_DIR/p1001.json"
python3 -m json.tool "$TEMP_DIR/p1001.json"

echo "[5/5] Low-risk office evaluation (P-2001)"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: demo-p2001' \
  -d '{"policyNo":"P-2001","question":"这张低风险办公楼财产险能否承保？"}' \
  >"$TEMP_DIR/p2001.json"
python3 -m json.tool "$TEMP_DIR/p2001.json"

echo "Demo completed. Swagger: $BASE_URL/swagger-ui/index.html | MCP: $BASE_URL/mcp"
