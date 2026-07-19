# 财险智能核保 Agent Demo

这是一个面向面试讲解和本地学习的 Java 财险智能核保后端。项目用一条完整、可运行、可追踪的链路展示：用户问题理解 → 内部资料查询 → RAG 知识检索 → 风险分析 → 确定性规则校验 → 大模型建议 → 结果持久化。

默认配置完全离线，使用虚构数据、Hash Embedding、内存向量库和确定性 Mock 模型，**无需 API Key**、数据库或外部模型即可启动。所有公司名、地址、条款和保单均为虚构示例，不包含真实业务数据。

## 技术栈

- Java 21、Maven
- Spring Boot 4.1、Spring MVC、Bean Validation、Actuator
- Spring JDBC、可选 H2 文件数据库 Profile
- Spring AI 2.0 MCP Server，Streamable HTTP `/mcp`
- OpenAI-compatible `/v1/chat/completions` 模型适配器
- springdoc OpenAPI / Swagger UI
- JUnit 5、AssertJ、MockMvc、MockWebServer

## 这个 Demo 能展示什么

- 会话管理：创建/恢复会话，按角色保存用户和助手消息。
- Agent 编排：固定七步流水线，成功或失败都保留步骤轨迹。
- 幂等与并发保护：可选 `Idempotency-Key` 把并发重复提交折叠为一次 Agent 执行，同键异参明确返回冲突。
- 异步任务编排：独立任务 REST API 返回 `202 Accepted`，后台执行七步 Agent，支持状态轮询、同键重放、有界线程池/队列和安全失败快照。
- RAG：Markdown/Text 解析、段落切分、重叠窗口、向量化、入库、余弦相似度检索和元数据过滤。
- 业务工具：保单、报价、历史核保、查勘报告、灾害风险和规则校验六类工具，共用统一注册表与审计轨迹。
- 分级故障策略：关键业务资料失败立即终止；灾害外部数据源可安全降级为未知，但强制提升到人工复核并保留结构化告警。
- 规则底线：模型只能解释规则结果，不能把 `MANUAL_REVIEW` 或 `REJECT` 降级为自动通过。
- 模型切换：默认 Mock；也可切到 OpenAI 兼容接口或企业私有化兼容网关，支持连接/读取超时、有限重试和显式降级。
- 提示词治理：版本化、激活版本、变量声明检查和预览渲染。
- REST + MCP：REST 用于管理和调试，MCP 用于 Agent 工具发现与调用，两者复用同一业务实现。
- 中文交互演示：无需复制命令即可选择四组虚构场景，运行真实核保并阅读规则、证据和七步轨迹。
- 中文核保报告：把已保存的单次评估导出为 Markdown，完整保留结论、规则、证据、七步轨迹、工具记录和模型元数据。
- 人工复核闭环：核保人可确认、推翻或要求补充资料；复核记录原子创建、不可覆盖，并进入报告和反馈导出接口。
- 可选重启恢复：`persistent-demo` Profile 把会话、评估和人工复核切换到 H2 文件库，进程重启后仍可查询；默认 Profile 继续使用内存仓储。
- 运行指标：Actuator 暴露评估、降级、人工复核和异步任务九组低基数 Micrometer 指标。

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

需要验证客户端重试安全时，可为提交请求增加稳定的 `Idempotency-Key`。首次成功返回 `201` 和
`Idempotency-Replayed: false`；相同键、相同业务载荷再次提交返回同一评估、HTTP `200` 和
`Idempotency-Replayed: true`。相同键绑定不同载荷会返回 `409 IDEMPOTENCY_KEY_CONFLICT`。

```bash
curl -i -X POST http://localhost:8080/api/v1/underwriting/evaluations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: interview-evaluation-001' \
  -d '{"policyNo":"P-2001","question":"这张办公楼保单是否可以承保？"}'

curl --fail http://localhost:8080/actuator/metrics/underwriting.evaluation.submissions
curl --fail http://localhost:8080/actuator/metrics/underwriting.evaluation.duration
curl --fail http://localhost:8080/actuator/metrics/underwriting.evaluation.decisions
```

幂等记录默认在单实例内保留 24 小时，最多 1,000 条；可通过 `IDEMPOTENCY_RETENTION` 和
`IDEMPOTENCY_MAX_ENTRIES` 调整。失败执行不会占用键，因此调用方可以使用原键重试。

### 异步任务编排

同步评估接口适合交互式请求；如果模型或内部系统响应较慢，可提交异步任务并轮询状态：

```bash
task_json=$(curl -sS --fail-with-body \
  -X POST http://localhost:8080/api/v1/underwriting/tasks \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: interview-task-001' \
  -d '{"policyNo":"P-1001","question":"异步分析这张仓库保单。"}')

task_id=$(printf '%s' "$task_json" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

curl --fail "http://localhost:8080/api/v1/underwriting/tasks/${task_id}" \
  | python3 -m json.tool
```

首次提交返回 `202 Accepted` 和任务 `Location`；状态按 `PENDING → RUNNING → SUCCEEDED/FAILED`
单向转换。成功任务给出 `evaluationId`，可继续读取评估和报告；失败任务只返回受控 `errorCode/message`，
不会把任意异常堆栈暴露给客户端。相同幂等键和载荷返回原任务及 `Idempotency-Replayed: true`；同键异参
返回 `409`。

执行器默认核心线程 2、最大线程 4、队列 100，任务记录最多保留 1,000 条、完成后保留 24 小时；可通过
`TASK_CORE_POOL_SIZE`、`TASK_MAX_POOL_SIZE`、`TASK_QUEUE_CAPACITY`、`TASK_MAX_ENTRIES` 和
`TASK_RETENTION` 调整。达到执行器或记录容量时明确返回 `503`，不会无界创建线程或堆积任务。

### 安全降级演示

默认启动不会注入故障。需要面试演示内部灾害平台超时时，启用专用 `degraded-demo` Profile：

```bash
SPRING_PROFILES_ACTIVE=degraded-demo mvn spring-boot:run
```

然后在 `/demo/` 运行 `P-2001`，或执行：

```bash
curl -sS -X POST http://localhost:8080/api/v1/underwriting/evaluations \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-2001","question":"灾害平台不可用时是否可以自动承保？"}' \
  | python3 -m json.tool

curl --fail http://localhost:8080/actuator/metrics/underwriting.agent.degradations
```

该 Profile 只模拟 `P-2001` 的灾害工具超时。返回仍保留基础风险 `LOW/10`，但灾害等级使用
`UNKNOWN`，最终决策提升为 `MANUAL_REVIEW`；`degradations` 会返回结构化告警码
`NON_CRITICAL_TOOL_UNAVAILABLE`，失败工具轨迹、`DEGRADED` 步骤、Markdown 报告和
Micrometer 指标也会记录原因。正常 Profile 下四个场景结果完全不变。
演示台会把这类结果标成“安全降级已覆盖常规场景预期”，而不是普通的结果不一致。

### 人工复核反馈闭环

任意单场景评估完成后，演示台会显示“人工复核反馈闭环”。核保人可以记录同意承保、拒绝承保或
要求补充资料。系统不会修改 Agent 原始建议，而是创建一条单独、不可覆盖的 `HumanReview`，并计算
`CONFIRMED`、`OVERRIDDEN`、`RESOLVED_MANUAL_REVIEW` 或 `CONTINUED_MANUAL_REVIEW` 关系。

```bash
curl -sS -X POST \
  http://localhost:8080/api/v1/underwriting/evaluations/EVAL-替换为真实编号/review \
  -H 'Content-Type: application/json' \
  -d '{
    "reviewerId":"UW-DEMO-001",
    "outcome":"APPROVED",
    "comment":"整改材料已核验，同意附条件承保。",
    "conditions":["提高免赔额","季度复查"]
  }' | python3 -m json.tool

curl --fail http://localhost:8080/actuator/metrics/underwriting.human.reviews
```

同一评估第二次提交会返回 `409 HUMAN_REVIEW_ALREADY_EXISTS`，避免覆盖审计结论。重新下载该评估的
Markdown 报告即可看到 Agent 辅助建议与人工最终处理的并列记录。

### 可选文件数据库与重启恢复

默认启动仍是最简单的内存模式，不需要数据库。需要在面试中演示“应用进程重启后评估和人工复核仍存在”时，
启用 `persistent-demo` Profile：

```bash
SPRING_PROFILES_ACTIVE=persistent-demo mvn spring-boot:run
```

首次运行会自动创建 `data/underwriting.mv.db` 及三张表。运行 `bash scripts/demo.sh` 生成评估和人工复核后，
停止应用并使用同一命令重新启动，再读取脚本输出中的评估编号：

```bash
curl --fail \
  http://localhost:8080/api/v1/underwriting/evaluations/EVAL-替换为真实编号 \
  | python3 -m json.tool

curl --fail \
  http://localhost:8080/api/v1/underwriting/evaluations/EVAL-替换为真实编号/review \
  | python3 -m json.tool
```

也可用环境变量指定数据库文件或账号：

```bash
export PERSISTENT_DB_URL='jdbc:h2:file:/tmp/underwriting-demo;DB_CLOSE_ON_EXIT=FALSE'
export PERSISTENT_DB_USERNAME=sa
export PERSISTENT_DB_PASSWORD=''
SPRING_PROFILES_ACTIVE=persistent-demo mvn spring-boot:run
```

这个 Profile 是“可证明跨进程持久化”的本地适配器，不是生产数据库方案。它只持久化会话、评估和人工复核；
知识库、Prompt 版本、异步任务状态和 `Idempotency-Key` 注册表仍在内存中。生产环境应改用 PostgreSQL/Redis、Flyway
版本化迁移、事务/乐观锁、追加式复核事件和多节点幂等，不应直接把 H2 文件放到多实例服务中共享。

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
3. 点击“运行智能核保”，查看实际结论、预期结论、规则命中、知识证据和七步执行轨迹；
4. 在“人工复核反馈闭环”中记录核保人的最终处理，观察它如何与 Agent 建议并列保留；
5. 点击“下载中文 Markdown 报告”，保存包含人工复核记录的可审计结果；
6. 点击“对比全部场景”，让四组虚构保单顺序调用同一个真实核保接口；
7. 从对比总览确认自动通过 1、人工复核 2、拒保 1，风险分范围为 10–70，再进入任一场景查看五类事实。

页面数据全部来自 `underwriting-scenarios.json`，仅用于教学和面试演示，不可用于真实承保判断。页面不复制业务判断，而是调用与 API 相同的核保服务。

横向对比不会读取预计算静态结论：四张卡片都来自实时 `POST /api/v1/underwriting/evaluations`。单组请求失败时，页面会保留其他成功结果并显示独立失败卡片。

下载链接只会在单场景评估成功后出现。后端读取该评估编号对应的已保存结果来生成报告，不会重新执行模型或规则；切换场景时旧链接会立即清理，避免下载错报告。

## 十分钟学习路线

1. 运行 `mvn clean verify`，确认环境和全部测试正常。
2. 运行 `mvn spring-boot:run` 启动离线服务。
3. 打开 `/demo/`，先点击“对比全部场景”理解结论分布，再进入一组场景运行详细核保。
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

一次完整评估会返回：最终决策、风险等级/分数、命中规则、知识证据、模型摘要、建议动作、结构化降级告警、5 次业务资料工具调用 + 1 次规则工具调用，以及 7 条步骤轨迹。正常流程步骤为 `SUCCESS`；允许继续但资料不完整时明确标记 `DEGRADED`，不会伪装成成功或低风险。

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
├── report     # 中文 Markdown 核保报告与安全转义
├── review     # 人工复核结果、关系分类、原子仓库与反馈指标
├── rule       # 确定性规则、风险分与决策下限
├── session    # 会话和消息
├── task       # 异步任务状态机、有界执行器与任务提交幂等
├── tool       # 虚构内部系统、注册表、MCP 工具
└── shared     # 配置、统一异常和持久化 JSON 编解码
```

进一步阅读：

- [虚构核保数据与十分钟学习指南](docs/DEMO_DATA_GUIDE.md)
- [架构与关键设计](docs/ARCHITECTURE.md)
- [完整 API / MCP 示例](docs/API_EXAMPLES.md)
- [面试演示与追问答案](docs/INTERVIEW_GUIDE.md)

## 生产化演进

这个项目有意把端口与内存/JDBC 实现分开，便于说明生产改造路线：

| Demo 实现 | 生产替换方向 |
|---|---|
| 默认内存仓储；可选 H2 聚合 JSON 仓储 | Redis 会话 + PostgreSQL 结构化审计与评估表、Flyway |
| H2 唯一键保护的单条不可覆盖复核 | PostgreSQL 追加式复核事件、RBAC、电子签名与事务 |
| Hash Embedding | 企业 Embedding 模型或合规云模型 |
| 内存向量库 | PostgreSQL + PGVector、Milvus 或 Elasticsearch |
| JSON 场景仓库 + 分级失败工具 | 带超时/熔断/舱壁和明确异常分类的内部 REST/gRPC/MQ 适配器 |
| 单机有界异步任务 + 单实例幂等 | 持久化状态机/工作流引擎、消息队列、跨节点幂等、超时取消与补偿 |
| 环境变量密钥 | Vault/KMS、短期凭证、密钥轮转和出口网关 |
| Micrometer 单实例指标 | Prometheus Registry、OpenTelemetry Trace、模型成本与召回质量看板 |

生产环境还应加入租户/机构权限、字段级脱敏、操作审计、知识发布审批、Prompt 灰度、脱敏反馈样本治理、模型输出评测和灾备策略。

## 免责声明

本项目仅用于技术学习和面试演示，不构成真实保险核保意见。示例数据、规则阈值和条款均为虚构内容。
