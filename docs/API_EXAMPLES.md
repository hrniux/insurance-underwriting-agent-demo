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
  -d '{"policyNo":"P-1001","question":"暴雨风险较高，这张仓库财产险能否承保？"}' \
  | python3 -m json.tool
```

```bash
curl -sS "$BASE_URL/api/v1/underwriting/evaluations/EVAL-替换为返回值" | python3 -m json.tool
curl -sS "$BASE_URL/api/v1/underwriting/evaluations" | python3 -m json.tool
```

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
