#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SUFFIX="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:12])')"
DOCUMENT_ID="DEMO-LIFECYCLE-${SUFFIX}"
OLD_TERM="legacy${SUFFIX}"
NEW_TERM="replacement${SUFFIX}"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

for attempt in $(seq 1 30); do
  if curl --silent --show-error --fail "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == "30" ]]; then
    echo "服务在 30 秒内未就绪：$BASE_URL" >&2
    exit 1
  fi
  sleep 1
done

search_term() {
  local term="$1"
  local target="$2"
  curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/knowledge/search" \
    -H 'Content-Type: application/json' \
    -d "{\"query\":\"$term\",\"topK\":20,\"productCode\":\"PROPERTY\"}" \
    >"$target"
}

assert_absent() {
  python3 - "$1" "$DOCUMENT_ID" <<'PY'
import json, sys
hits = json.load(open(sys.argv[1]))
document_id = sys.argv[2]
if any(hit["chunk"]["documentId"] == document_id for hit in hits):
    raise SystemExit(f"未发布或已下线文档仍可被检索：{document_id}")
PY
}

assert_present_version() {
  python3 - "$1" "$DOCUMENT_ID" "$2" <<'PY'
import json, sys
hits = json.load(open(sys.argv[1]))
document_id, expected_version = sys.argv[2], sys.argv[3]
matched = [hit for hit in hits if hit["chunk"]["documentId"] == document_id]
if not matched:
    raise SystemExit(f"已发布文档未被检索：{document_id}")
actual_version = matched[0]["chunk"]["metadata"].get("knowledgeVersion")
if actual_version != expected_version:
    raise SystemExit(f"索引版本错误：expected={expected_version}, actual={actual_version}")
PY
}

assert_version_not_indexed() {
  python3 - "$1" "$DOCUMENT_ID" "$2" "$3" <<'PY'
import json, sys
hits = json.load(open(sys.argv[1]))
document_id, forbidden_version, forbidden_term = sys.argv[2:5]
matched = [hit for hit in hits if hit["chunk"]["documentId"] == document_id]
for hit in matched:
    chunk = hit["chunk"]
    actual_version = chunk["metadata"].get("knowledgeVersion")
    if actual_version == forbidden_version or forbidden_term in chunk["content"]:
        raise SystemExit(
            f"未发布版本进入索引：document={document_id}, version={actual_version}"
        )
PY
}

assert_replaced_by_version() {
  python3 - "$1" "$DOCUMENT_ID" "$2" "$3" <<'PY'
import json, sys
hits = json.load(open(sys.argv[1]))
document_id, expected_version, retired_term = sys.argv[2:5]
matched = [hit for hit in hits if hit["chunk"]["documentId"] == document_id]
for hit in matched:
    chunk = hit["chunk"]
    actual_version = chunk["metadata"].get("knowledgeVersion")
    if actual_version != expected_version or retired_term in chunk["content"]:
        raise SystemExit(
            f"旧索引未被完整替换：expected={expected_version}, actual={actual_version}"
        )
PY
}

echo "[1/8] 创建 v1 草稿（不会进入检索）"
curl --silent --show-error --fail -X POST "$BASE_URL/api/v1/knowledge/documents" \
  -H 'Content-Type: application/json' \
  -d "{\"id\":\"$DOCUMENT_ID\",\"title\":\"知识生命周期演示\",\"type\":\"UNDERWRITING_RULE\",\"productCode\":\"PROPERTY\",\"content\":\"$OLD_TERM 初版核保规则。\",\"metadata\":{\"source\":\"lifecycle-demo-v1\"}}" \
  >"$TEMP_DIR/draft-v1.json"
python3 -m json.tool "$TEMP_DIR/draft-v1.json"

echo "[2/8] 验证 v1 草稿不可召回"
search_term "$OLD_TERM" "$TEMP_DIR/search-draft-v1.json"
assert_absent "$TEMP_DIR/search-draft-v1.json"

echo "[3/8] 发布 v1 并验证索引版本"
curl --silent --show-error --fail -X POST \
  "$BASE_URL/api/v1/knowledge/documents/$DOCUMENT_ID/versions/1/publish" \
  >"$TEMP_DIR/publish-v1.json"
python3 -m json.tool "$TEMP_DIR/publish-v1.json"
search_term "$OLD_TERM" "$TEMP_DIR/search-published-v1.json"
assert_present_version "$TEMP_DIR/search-published-v1.json" "1"

echo "[4/8] 创建 v2 草稿，v1 继续在线"
curl --silent --show-error --fail -X POST \
  "$BASE_URL/api/v1/knowledge/documents/$DOCUMENT_ID/versions" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"知识生命周期演示修订版\",\"type\":\"UNDERWRITING_RULE\",\"productCode\":\"PROPERTY\",\"content\":\"$NEW_TERM 修订后的核保规则。\",\"metadata\":{\"source\":\"lifecycle-demo-v2\"}}" \
  >"$TEMP_DIR/draft-v2.json"
python3 -m json.tool "$TEMP_DIR/draft-v2.json"
search_term "$NEW_TERM" "$TEMP_DIR/search-draft-v2.json"
assert_version_not_indexed "$TEMP_DIR/search-draft-v2.json" "2" "$NEW_TERM"
search_term "$OLD_TERM" "$TEMP_DIR/search-still-v1.json"
assert_present_version "$TEMP_DIR/search-still-v1.json" "1"

echo "[5/8] 发布 v2，原子替换 v1 索引"
curl --silent --show-error --fail -X POST \
  "$BASE_URL/api/v1/knowledge/documents/$DOCUMENT_ID/versions/2/publish" \
  >"$TEMP_DIR/publish-v2.json"
python3 -m json.tool "$TEMP_DIR/publish-v2.json"
search_term "$OLD_TERM" "$TEMP_DIR/search-retired-v1.json"
assert_replaced_by_version "$TEMP_DIR/search-retired-v1.json" "2" "$OLD_TERM"
search_term "$NEW_TERM" "$TEMP_DIR/search-published-v2.json"
assert_present_version "$TEMP_DIR/search-published-v2.json" "2"

echo "[6/8] 检查版本历史：v1 RETIRED、v2 PUBLISHED"
curl --silent --show-error --fail \
  "$BASE_URL/api/v1/knowledge/documents/$DOCUMENT_ID/versions" \
  >"$TEMP_DIR/versions.json"
python3 - "$TEMP_DIR/versions.json" <<'PY'
import json, sys
versions = json.load(open(sys.argv[1]))
statuses = [version["status"] for version in versions]
if statuses != ["RETIRED", "PUBLISHED"]:
    raise SystemExit(f"版本状态错误：{statuses}")
print(json.dumps(versions, ensure_ascii=False, indent=2))
PY

echo "[7/8] 下线 v2"
curl --silent --show-error --fail -X POST \
  "$BASE_URL/api/v1/knowledge/documents/$DOCUMENT_ID/versions/2/retire" \
  >"$TEMP_DIR/retire-v2.json"
python3 -m json.tool "$TEMP_DIR/retire-v2.json"

echo "[8/8] 验证下线后不可召回"
search_term "$NEW_TERM" "$TEMP_DIR/search-retired-v2.json"
assert_absent "$TEMP_DIR/search-retired-v2.json"

echo "知识版本生命周期演示完成：$DOCUMENT_ID"
