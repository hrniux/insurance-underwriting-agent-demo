# 财险智能核保 Agent Demo

这是一个面向面试讲解和本地学习的 Java 财险智能核保后端。项目用一条完整、可运行、可追踪的链路展示：用户问题理解 → 内部资料查询 → RAG 知识检索 → 风险分析 → 确定性规则校验 → 大模型建议 → 结果持久化。

默认配置完全离线，使用虚构数据、Hash Embedding、内存向量库和确定性 Mock 模型，**无需 API Key**、数据库或外部模型即可启动。所有公司名、地址、条款和保单均为虚构示例，不包含真实业务数据。

## 技术栈

- Java 21、Maven
- Spring Boot 4.1、Spring MVC、Bean Validation、Actuator
- Spring AI 2.0 MCP Server，Streamable HTTP `/mcp`
- OpenAI-compatible `/v1/chat/completions` 模型适配器
- springdoc OpenAPI / Swagger UI
- JUnit 5、AssertJ、MockMvc、MockWebServer

## 这个 Demo 能展示什么

- 会话管理：创建/恢复会话，按角色保存用户和助手消息。
- Agent 编排：固定七步流水线，成功或失败都保留步骤轨迹。
- RAG：Markdown/Text 解析、段落切分、重叠窗口、向量化、入库、余弦相似度检索和元数据过滤。
- 业务工具：保单、报价、历史核保、查勘报告、灾害风险和规则校验六类工具，共用统一注册表与审计轨迹。
- 规则底线：模型只能解释规则结果，不能把 `MANUAL_REVIEW` 或 `REJECT` 降级为自动通过。
- 模型切换：默认 Mock；也可切到 OpenAI 兼容接口或企业私有化兼容网关，支持连接/读取超时、有限重试和显式降级。
- 提示词治理：版本化、激活版本、变量声明检查和预览渲染。
- REST + MCP：REST 用于管理和调试，MCP 用于 Agent 工具发现与调用，两者复用同一业务实现。
- 中文交互演示：无需复制命令即可选择四组虚构场景，运行真实核保并阅读规则、证据和七步轨迹。

## 快速启动

前提：本机有 Java 21+ 和 Maven 3.9+。

```bash
mvn clean verify
mvn spring-boot:run
```

新开终端检查服务：

```bash
curl --fail http://localhost:8080/actuator/health
bash scripts/demo.sh
```

浏览器访问：

- 中文智能核保演示台：<http://localhost:8080/demo/>（推荐首次体验）
- Swagger UI：<http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 虚构场景目录：<http://localhost:8080/api/v1/demo/scenarios>
- MCP Streamable HTTP：`http://localhost:8080/mcp`

### 浏览器交互演示（推荐）

启动应用后访问 <http://localhost:8080/demo/>，进入“中文智能核保演示台”。

1. 从左侧选择 `P-1001` 至 `P-4001` 任一虚构场景；
2. 阅读保单、报价、历史、查勘和灾害五类业务事实；
3. 点击“运行智能核保”，查看实际结论、预期结论、规则命中、知识证据和七步执行轨迹。

页面数据全部来自 `underwriting-scenarios.json`，仅用于教学和面试演示，不可用于真实承保判断。页面不复制业务判断，而是调用与 API 相同的核保服务。

## 十分钟学习路线

1. 运行 `mvn clean verify`，确认环境和全部测试正常。
2. 运行 `mvn spring-boot:run` 启动离线服务。
3. 打开 `/demo/`，选择一组场景并运行一次可视化核保。
4. 打开 `/api/v1/demo/scenarios` 对照页面数据，再运行 `bash scripts/demo.sh` 观察批量 API 流程。
5. 对照 [虚构核保数据与十分钟学习指南](docs/DEMO_DATA_GUIDE.md)，从业务事实追踪到规则命中、风险加分和最终决策。

## 四个教学演示场景

| 保单号 | 场景 | 关键事实 | 确定性结果 |
|---|---|---|---|
| `P-1001` | 临港高风险物流仓库 | 保额 2,000 万元、近三年 2 次出险、排水整改未完成、暴雨红色风险 | 人工复核（`MANUAL_REVIEW`）、高风险（`HIGH`）、70 分 |
| `P-2001` | 低风险科技办公楼 | 保额 500 万元、无历史出险、无查勘遗留、灾害风险低 | 证据齐全时自动通过（`APPROVE`）、低风险（`LOW`）、10 分 |
| `P-3001` | 暴雨暴露商贸仓库 | 保额 1,200 万元、1 次出险、整改完成、暴雨红色风险 | 人工复核（`MANUAL_REVIEW`）、中风险（`MEDIUM`）、40 分 |
| `P-4001` | 极端火灾风险制造厂房 | 保额 800 万元、消防重大缺陷、火灾风险极端 | 拒保（`REJECT`）、高风险（`HIGH`）、60 分 |

知识证据缺失时，即使规则原本允许自动通过，系统也会把决策下限提升到 `MANUAL_REVIEW`，避免无依据自动承保。

## 七步编排

```text
QUESTION_UNDERSTANDING
  -> BUSINESS_DATA_COLLECTION
  -> KNOWLEDGE_RETRIEVAL
  -> RISK_ANALYSIS
  -> RULE_VALIDATION
  -> RECOMMENDATION_GENERATION
  -> RESULT_PERSISTENCE
```

一次完整评估会返回：最终决策、风险等级/分数、命中规则、知识证据、模型摘要、建议动作、5 次业务资料工具调用 + 1 次规则工具调用，以及 7 条步骤轨迹。

## 模型切换

默认值 `LLM_PROVIDER=mock`，不访问网络。连接 OpenAI 兼容或企业私有化接口时：

```bash
export LLM_PROVIDER=openai-compatible
export LLM_BASE_URL=https://your-model-gateway.example.com
export LLM_API_KEY=replace-with-runtime-secret
export LLM_MODEL=your-model-name
export LLM_CONNECT_TIMEOUT=2s
export LLM_READ_TIMEOUT=15s
export LLM_MAX_ATTEMPTS=3
export LLM_RETRY_BACKOFF=100ms
export LLM_FALLBACK_TO_MOCK=false
mvn spring-boot:run
```

只对 I/O 异常、超时、HTTP 429 和 5xx 重试；普通 4xx 不重试。默认禁止静默降级。只有显式设置 `LLM_FALLBACK_TO_MOCK=true` 才回退，并在响应中标记 `fallbackUsed=true`。API Key 只从运行时环境读取，不写日志、不写响应、不写异常。

## Docker

```bash
docker build -t insurance-underwriting-agent-demo:local .
docker run --rm -p 8080:8080 insurance-underwriting-agent-demo:local
```

容器使用 Java 21 运行时、非 root 用户和健康检查。默认仍为无外部依赖的 Mock 模式。

## 项目结构

```text
src/main/java/com/hrniux/underwriting
├── agent      # 七步编排、评估结果与步骤轨迹
├── api        # REST 控制器和请求 DTO
├── demo       # 结构化虚构场景、完整性校验和中文目录 API
├── model      # Mock/OpenAI-compatible/路由模型网关
├── prompt     # 提示词版本管理和严格渲染
├── rag        # 文档解析、切分、Embedding、向量检索
├── rule       # 确定性规则、风险分与决策下限
├── session    # 会话和消息
├── tool       # 虚构内部系统、注册表、MCP 工具
└── shared     # 配置和统一异常
```

进一步阅读：

- [虚构核保数据与十分钟学习指南](docs/DEMO_DATA_GUIDE.md)
- [架构与关键设计](docs/ARCHITECTURE.md)
- [完整 API / MCP 示例](docs/API_EXAMPLES.md)
- [面试演示与追问答案](docs/INTERVIEW_GUIDE.md)

## 生产化演进

这个项目有意把端口与内存实现分开，便于说明生产改造路线：

| Demo 实现 | 生产替换方向 |
|---|---|
| 内存会话/评估仓库 | Redis 会话 + PostgreSQL 审计与评估表 |
| Hash Embedding | 企业 Embedding 模型或合规云模型 |
| 内存向量库 | PostgreSQL + PGVector、Milvus 或 Elasticsearch |
| JSON 场景仓库 + 虚构工具 | 保单、报价、核保、查勘、灾害平台和规则引擎的真实 API 适配器 |
| 单机同步编排 | 状态机/工作流引擎、消息队列、幂等键、超时补偿 |
| 环境变量密钥 | Vault/KMS、短期凭证、密钥轮转和出口网关 |
| 单实例指标 | Micrometer、Prometheus、OpenTelemetry Trace、模型成本与召回质量看板 |

生产环境还应加入租户/机构权限、字段级脱敏、操作审计、知识发布审批、Prompt 灰度、模型输出评测、人工反馈闭环和灾备策略。

## 免责声明

本项目仅用于技术学习和面试演示，不构成真实保险核保意见。示例数据、规则阈值和条款均为虚构内容。
