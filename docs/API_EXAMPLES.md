# API 与 MCP 调用示例

以下命令假设服务运行在 `http://localhost:8080`。

```bash
export BASE_URL=http://localhost:8080
```

## 健康、OpenAPI 与 Swagger

```bash
curl --fail "$BASE_URL/actuator/health"
curl --fail "$BASE_URL/v3/api-docs" | python3 -m json.tool
open "$BASE_URL/swagger-ui/index.html"
```

## 虚构演示场景 `/api/v1/demo/scenarios`

先查看四组场景摘要，再查看单个场景的完整五类业务事实：

```bash
curl -sS "$BASE_URL/api/v1/demo/scenarios" | python3 -m json.tool
curl -sS "$BASE_URL/api/v1/demo/scenarios/P-3001" | python3 -m json.tool
```

响应同时包含稳定的英文枚举和中文显示值：

```json
{
  "policyNo": "P-3001",
  "name": "暴雨暴露商贸仓库",
  "expectedResult": {
    "decision": "MANUAL_REVIEW",
    "decisionLabel": "人工复核",
    "riskLevel": "MEDIUM",
    "riskLevelLabel": "中风险",
    "riskScore": 40
  },
  "sumInsuredDisplay": "1,200 万元"
}
```

场景接口只读取虚构教学数据，不创建会话，也不执行核保评估。

## 会话 `/api/v1/sessions`

```bash
curl -sS -X POST "$BASE_URL/api/v1/sessions" | python3 -m json.tool
curl -sS "$BASE_URL/api/v1/sessions/SES-替换为返回值" | python3 -m json.tool
```

## 核保评估 `/api/v1/underwriting/evaluations`

不传 `sessionId` 时自动创建会话：

```bash
curl -sS -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: interview-demo-001' \
  -H 'Idempotency-Key: interview-demo-001' \
  -d '{"policyNo":"P-1001","question":"暴雨风险较高，这张仓库财产险能否承保？"}' \
  | python3 -m json.tool
```

把上面的请求原样执行两次：第一次返回 `201`、响应头 `Idempotency-Replayed: false`；第二次
不再执行七步 Agent，而是返回相同评估编号、HTTP `200` 和 `Idempotency-Replayed: true`。
同一个键改用不同 `policyNo`、`sessionId` 或 `question` 会返回 `409`：

```json
{
  "status": 409,
  "errorCode": "IDEMPOTENCY_KEY_CONFLICT",
  "detail": "Idempotency-Key has already been used with a different request"
}
```

键只能使用字母、数字、点、下划线、冒号和连字符，长度 1–128。失败执行不缓存；同一个键可重试。

```bash
curl -sS "$BASE_URL/api/v1/underwriting/evaluations/EVAL-替换为返回值" | python3 -m json.tool
curl -sS "$BASE_URL/api/v1/underwriting/evaluations" | python3 -m json.tool
```

### Actuator 运行指标

至少提交一次评估后查询：

```bash
curl -sS "$BASE_URL/actuator/metrics/underwriting.evaluation.submissions" | python3 -m json.tool
curl -sS "$BASE_URL/actuator/metrics/underwriting.evaluation.duration" | python3 -m json.tool
curl -sS "$BASE_URL/actuator/metrics/underwriting.evaluation.decisions" | python3 -m json.tool
```

提交计数和耗时按 `outcome=created|replayed|conflict|failed` 聚合；决策计数只记录真实 Agent
执行，按 `decision` 和 `risk_level` 聚合，不把重放请求重复计入业务结论。

提交人工复核后还可以查询：

```bash
curl -sS "$BASE_URL/actuator/metrics/underwriting.human.reviews" | python3 -m json.tool
curl -sS "$BASE_URL/actuator/metrics/underwriting.human.review.delay" | python3 -m json.tool
```

二者只使用 `outcome` 和 `relationship` 枚举标签，不使用人员、评估或保单编号。

### 可重复的灾害平台降级演示

先停止普通进程，然后使用专用 Profile 启动：

```bash
SPRING_PROFILES_ACTIVE=degraded-demo mvn spring-boot:run
```

提交 `P-2001`：

```bash
curl -sS -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: degraded-demo-p2001' \
  -d '{"policyNo":"P-2001","question":"灾害平台不可用时是否可以自动承保？"}' \
  | python3 -m json.tool
```

关键响应片段：

```json
{
  "decision": "MANUAL_REVIEW",
  "riskLevel": "LOW",
  "riskScore": 10,
  "degradations": [{
    "code": "NON_CRITICAL_TOOL_UNAVAILABLE",
    "toolName": "GET_DISASTER_RISK",
    "errorCode": "TOOL_CALL_FAILED",
    "decisionFloor": "MANUAL_REVIEW"
  }],
  "stepTraces": [
    {"step": "BUSINESS_DATA_COLLECTION", "status": "DEGRADED",
     "errorCode": "NON_CRITICAL_TOOL_UNAVAILABLE"}
  ]
}
```

工具轨迹中的 `GET_DISASTER_RISK` 状态为 `FAILED`；后续规则、模型和持久化步骤仍可完成。下载该
评估的 Markdown 报告会出现“数据质量与安全降级”章节。这个 Profile 只影响 `P-2001`，且仅用于
教学和面试，不应作为生产故障注入机制。

发生一次降级后可查询对应指标：

```bash
curl -sS "$BASE_URL/actuator/metrics/underwriting.agent.degradations" | python3 -m json.tool
```

### 人工复核反馈 `/{evaluationId}/review`

先创建一条需要人工复核的虚构评估：

```bash
evaluation_json=$(curl -sS --fail-with-body \
  -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-1001","question":"请人工确认是否可以附条件承保。"}')

evaluation_id=$(printf '%s' "$evaluation_json" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

curl -sS --fail-with-body \
  -X POST "$BASE_URL/api/v1/underwriting/evaluations/${evaluation_id}/review" \
  -H 'Content-Type: application/json' \
  -d '{
    "reviewerId":"UW-DEMO-001",
    "outcome":"APPROVED",
    "comment":"整改材料已核验，同意附条件承保。",
    "conditions":["提高免赔额至 10 万元","每季度复查排水设施"]
  }' | python3 -m json.tool
```

关键响应片段：

```json
{
  "evaluationId": "EVAL-...",
  "reviewerId": "UW-DEMO-001",
  "outcome": "APPROVED",
  "relationship": "RESOLVED_MANUAL_REVIEW",
  "conditions": ["提高免赔额至 10 万元", "每季度复查排水设施"]
}
```

读取单条复核或导出当前 Demo 中的全部复核：

```bash
curl -sS "$BASE_URL/api/v1/underwriting/evaluations/${evaluation_id}/review" | python3 -m json.tool
curl -sS "$BASE_URL/api/v1/underwriting/reviews" | python3 -m json.tool
```

同一评估只允许创建一次；再次 `POST` 返回 `409 HUMAN_REVIEW_ALREADY_EXISTS`。系统保留原始 Agent
建议，不把人工结果回写覆盖。生产环境导出反馈前必须进行权限校验、字段脱敏、用途审批和留存治理。

### 下载中文 Markdown 报告 `/{evaluationId}/report`

报告接口只读取已经保存的评估，不会再次运行七步 Agent。下面先用虚构保单 `P-1001` 创建评估，再保存报告和响应头：

```bash
evaluation_json=$(curl -sS --fail-with-body \
  -X POST "$BASE_URL/api/v1/underwriting/evaluations" \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-1001","question":"请生成一份便于讲解的核保结论。"}')

evaluation_id=$(printf '%s' "$evaluation_json" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

curl -sS --fail-with-body \
  -D report-headers.txt \
  -o "underwriting-report-${evaluation_id}.md" \
  "$BASE_URL/api/v1/underwriting/evaluations/${evaluation_id}/report"

grep -i '^Content-' report-headers.txt
sed -n '1,80p' "underwriting-report-${evaluation_id}.md"
```

稳定的路径模板是 `GET /api/v1/underwriting/evaluations/{evaluationId}/report`。成功响应使用 `text/markdown;charset=UTF-8`，并通过 `Content-Disposition` 给出 `underwriting-report-{evaluationId}.md` 附件名；评估不存在时仍返回统一的 `EVALUATION_NOT_FOUND` Problem Details。

## 知识文档 `/api/v1/knowledge/documents`

```bash
curl -sS "$BASE_URL/api/v1/knowledge/documents" | python3 -m json.tool
```

```bash
curl -sS -X POST "$BASE_URL/api/v1/knowledge/documents" \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"DEMO-CLAUSE-001",
    "title":"演示附加条款",
    "type":"PRODUCT_CLAUSE",
    "productCode":"PROPERTY",
    "content":"暴雨红色风险区域应转人工复核，并核验防洪整改证明。",
    "metadata":{"source":"interview-demo"}
  }' | python3 -m json.tool
```

## RAG 检索 `/api/v1/knowledge/search`

```bash
curl -sS -X POST "$BASE_URL/api/v1/knowledge/search" \
  -H 'Content-Type: application/json' \
  -d '{"query":"仓库暴雨红色风险如何核保","topK":4,"productCode":"PROPERTY"}' \
  | python3 -m json.tool
```

`documentType` 可传 `PRODUCT_CLAUSE`、`UNDERWRITING_RULE`、`RISK_GUIDE` 或 `HISTORICAL_CASE`。

## 提示词 `/api/v1/prompts`

```bash
curl -sS "$BASE_URL/api/v1/prompts" | python3 -m json.tool
curl -sS "$BASE_URL/api/v1/prompts/underwriting-analysis" | python3 -m json.tool
```

创建和激活版本：

```bash
curl -sS -X POST "$BASE_URL/api/v1/prompts/demo-summary/versions" \
  -H 'Content-Type: application/json' \
  -d '{"body":"问题：{{question}}","requiredVariables":["question"]}' \
  | python3 -m json.tool

curl -sS -X POST "$BASE_URL/api/v1/prompts/demo-summary/versions/1/activate" \
  | python3 -m json.tool
```

预览：

```bash
curl -sS -X POST "$BASE_URL/api/v1/prompts/demo-summary/preview" \
  -H 'Content-Type: application/json' \
  -d '{"variables":{"question":"是否承保"}}' \
  | python3 -m json.tool
```

## 工具调试 `/api/v1/tools`

```bash
curl -sS "$BASE_URL/api/v1/tools" | python3 -m json.tool
curl -sS -X POST "$BASE_URL/api/v1/tools/GET_POLICY/invoke" \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-1001"}' | python3 -m json.tool
curl -sS -X POST "$BASE_URL/api/v1/tools/VALIDATE_RULES/invoke" \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-1001"}' | python3 -m json.tool
```

## MCP Streamable HTTP `/mcp`

初始化请求：

```bash
curl -i -sS -X POST "$BASE_URL/mcp" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"initialize",
    "params":{
      "protocolVersion":"2025-06-18",
      "capabilities":{},
      "clientInfo":{"name":"curl-interview-demo","version":"1.0.0"}
    }
  }'
```

真实 MCP 客户端会保存初始化响应中的 `Mcp-Session-Id`，发送 `notifications/initialized`，然后调用 `tools/list` 或 `tools/call`。服务器暴露：

```text
get_policy
get_quotation
get_underwriting_history
get_survey_report
get_disaster_risk
validate_rules
```

推荐用支持 Streamable HTTP 的 MCP Inspector/SDK 连接 `http://localhost:8080/mcp`，避免手工处理会话头和 SSE 帧。

## 统一错误格式

```bash
curl -sS "$BASE_URL/api/v1/sessions/SES-MISSING" \
  -H 'X-Trace-Id: demo-error-001' | python3 -m json.tool
```

响应包含标准 Problem Details 字段以及：

```json
{
  "status": 404,
  "errorCode": "SESSION_NOT_FOUND",
  "traceId": "demo-error-001",
  "timestamp": "2026-07-13T00:00:00Z"
}
```
