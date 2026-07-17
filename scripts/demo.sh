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
  echo "服务在 30 秒内未就绪：$BASE_URL" >&2
  exit 1
fi

evaluate_scenario() {
  local step="$1"
  local title="$2"
  local policy_no="$3"
  local question="$4"
  local output_file="$TEMP_DIR/${policy_no}.json"

  echo "[$step/8] ${title}（${policy_no}）"
  curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
    -H 'Content-Type: application/json' \
    -H "X-Trace-Id: demo-${policy_no}" \
    -d "{\"policyNo\":\"$policy_no\",\"question\":\"$question\"}" \
    >"$output_file"
  python3 -m json.tool "$output_file"
}

echo "[1/8] 服务健康检查"
python3 -m json.tool "$health_file"

echo "[2/8] 演示场景目录"
curl --silent --show-error --fail "$BASE_URL/api/v1/demo/scenarios" >"$TEMP_DIR/scenarios.json"
python3 -m json.tool "$TEMP_DIR/scenarios.json"

echo "[3/8] RAG 知识检索"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/knowledge/search" \
  -H 'Content-Type: application/json' \
  -d '{"query":"仓库暴雨红色风险如何核保","topK":4,"productCode":"PROPERTY"}' \
  >"$TEMP_DIR/search.json"
python3 -m json.tool "$TEMP_DIR/search.json"

echo "[4/8] 共享业务工具"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/tools/GET_POLICY/invoke" \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-1001"}' \
  >"$TEMP_DIR/tool.json"
python3 -m json.tool "$TEMP_DIR/tool.json"

evaluate_scenario 5 "高风险仓库：人工复核" "P-1001" "暴雨风险较高，这张仓库财产险能否承保？"
evaluate_scenario 6 "低风险办公楼：自动通过" "P-2001" "这张低风险办公楼财产险能否承保？"
evaluate_scenario 7 "中风险仓库：人工复核" "P-3001" "这张暴雨风险仓库保单为什么需要人工复核？"
evaluate_scenario 8 "极端火灾厂房：拒保" "P-4001" "这张存在重大消防缺陷的厂房保单能否承保？"

echo "中文演示已完成。"
echo "Swagger：$BASE_URL/swagger-ui/index.html"
echo "场景目录：$BASE_URL/api/v1/demo/scenarios"
echo "教学指南：docs/DEMO_DATA_GUIDE.md"
echo "MCP：$BASE_URL/mcp"
