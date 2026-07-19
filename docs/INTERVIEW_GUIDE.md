# 面试演示与问答指南

## 一句话项目介绍

这是一个财险智能核保辅助决策后端：它把分散在保单、报价、历史核保、风险查勘、灾害平台、核保规则和产品条款中的信息，通过工具调用、RAG、确定性规则和模型生成串成可解释、可审计的 Agent 流程。

## 五分钟演示流程

### 第 0–1 分钟：说明边界并打开浏览器演示台

运行 `mvn spring-boot:run`，打开 <http://localhost:8080/demo/>。说明 Agent 负责资料聚合、规则检查和建议整理，确定性规则拥有决策底线；页面全部数据均为虚构数据。

### 第 1–2 分钟：横向比较四组真实结果

点击“对比全部场景”，说明页面顺序调用四次真实核保接口，不展示预计算截图。总览应显示自动通过 1、人工复核 2、拒保 1、风险分 10–70，四组实际结论与预期一致。

### 第 2–3 分钟：展开一组规则、RAG 与轨迹

进入 `P-1001`，点击“运行智能核保”，展示 70 分、四条规则、四条知识证据、七步 Agent 轨迹和六类工具调用，并说明规则结果不能被模型文本降低。随后提交一条“同意附条件承保”的人工复核，最后下载 Markdown 报告，展示 Agent 原始建议与人工最终处置如何形成可审计交付物。

### 第 3–4 分钟：说明 REST、MCP 与模型边界

指出浏览器演示台、REST 调试接口和六个真实 `@McpTool` 复用同一套工具与规则逻辑；默认 Mock 无需 API Key，OpenAI-compatible 网关负责超时、重试和显式降级。

### 第 4–5 分钟：讲可靠性与生产化

提交一次异步任务，说明 `202 + Location`、四态状态机、有界线程池/队列和任务级 `Idempotency-Key`；可切换 `degraded-demo` Profile 展示灾害平台超时后仍保持人工复核底线，再切到 `persistent-demo` 说明仓储端口不变、重启后仍能读到评估与复核。展示九组 Actuator 指标后，给出工作流引擎、Redis、PostgreSQL/PGVector、真实内部 API、企业模型和 OpenTelemetry 的替换路径。

## 简历职责如何映射到代码

| 简历职责 | Demo 代码 |
|---|---|
| 会话管理、任务编排、模型封装、提示词管理 | `session`、`agent`、`model`、`prompt` 包 |
| RAG 解析、切分、向量化入库 | `KnowledgeService`、`ParagraphTextSplitter`、`HashEmbeddingService`、`VectorStore` |
| 模型切换、超时、重试 | `RoutingModelGateway`、`OpenAiCompatibleModelGateway` |
| 内部接口封装为 Agent/MCP 工具 | `UnderwritingFactTools`、`ToolRegistry`、`UnderwritingMcpTools` |
| 规则校验与可解释决策 | `UnderwritingRuleEngine`、`RuleEvaluation`、步骤/工具 Trace |
| 可审计中文结果交付 | `UnderwritingMarkdownReportService`、`GET /api/v1/underwriting/evaluations/{evaluationId}/report` |
| 幂等、并发单飞与运行指标 | `EvaluationSubmissionService`、`Idempotency-Key`、Actuator Metrics |
| 内部系统故障分级与安全降级 | `ToolCriticality`、`ToolAttempt`、`DegradationNotice`、`degraded-demo` Profile |
| 人工复核与 Agent 效果反馈闭环 | `HumanReviewService`、`HumanReviewRepository`、Review API、人工复核指标 |
| 会话、评估与复核的重启恢复 | `persistent-demo`、三个 `Jdbc*Repository`、H2 Schema、Repository 端口隔离 |
| 异步任务编排与过载保护 | `UnderwritingTaskService`、四态状态机、`ThreadPoolTaskExecutor`、任务级幂等和任务指标 |

## 常见面试问题

### Q1：为什么规则引擎和大模型都要有？

规则适合监管、条款和硬性风险边界，结果稳定且可审计；模型适合非结构化资料理解和解释生成。二者组合后，模型提升效率，规则控制风险。本项目最终决策只从规则结果和证据下限产生，模型不能降级。

### Q2：这个 RAG 为什么不用真实 Embedding？

Demo 首要目标是零外部依赖、可重复测试。Hash Embedding 能完整展示解析、切分、向量化和召回接口。生产环境会保持 `EmbeddingService` 端口，替换企业 Embedding，并用标注集评估 Recall@K、MRR 和业务命中率。

### Q3：如何避免 RAG 幻觉或引用错误？

保留 document/chunk ID、类型、标题、片段和分数；限制产品代码与文档类型；没有证据时提高人工复核下限；生产上再加发布审批、版本、生效日期、权限过滤、重排模型和引用一致性校验。

### Q4：为什么选择 MCP？

MCP 给工具提供统一发现、输入 Schema 和调用协议，便于不同 Agent/模型客户端复用。项目用 Spring AI 的真实 Streamable HTTP Server；REST 只用于管理和调试，二者共用 `ToolRegistry`，避免逻辑分叉。

### Q5：模型调用如何处理重试？

只重试网络 I/O、超时、429 和 5xx，最大次数和退避可配置；普通 4xx 通常是请求问题，立即失败。读取和连接分别设超时，线程中断会恢复。响应记录实际 attempts。

### Q6：为什么默认不自动降级 Mock？

静默降级可能让调用方误以为使用了企业模型。默认直接返回 `MODEL_UNAVAILABLE`；只有显式开启时才回退，并把 `fallbackUsed=true` 放进响应，让审计和调用方都能识别。

### Q7：如何保证 API Key 不泄露？

密钥来自环境变量，只用于 Authorization Header；对象 `toString` 主动脱敏，异常使用固定安全信息，不拼接请求头或完整响应。生产上还应接 Vault/KMS、出口代理、日志脱敏与密钥轮转。

### Q8：并发下内存实现安全吗？

Demo 使用不可变 record、`ConcurrentHashMap` 和复制后返回。评估提交额外支持 `Idempotency-Key`：首个请求认领执行权，并发重复请求等待同一个 `CompletableFuture`；同键异参返回 409；失败后释放键。记录有容量和 TTL 边界，但只在单实例有效。生产应把状态迁移到 Redis 或数据库唯一键，并结合事务、乐观锁和工作流实例级并发控制。

### Q9：规则如何扩展和灰度？

每条规则实现同一接口，返回代码、原因、严重度、分数、决策和优先级。生产可把阈值外置到版本化规则配置，增加生效时间、机构/产品范围、审批流、影子运行和命中率监控。

### Q10：提示词为什么要版本化？

提示词直接影响模型输出，需要可追踪、可回滚。服务保存版本、激活状态和必需变量；创建时检查声明与占位符一致，渲染时拒绝缺失变量。生产再加审批、灰度和效果评测。

### Q11：怎么做可观测性？

当前返回每一步和每个工具的状态、耗时、错误码，模型响应含 provider/model/attempts/fallback；同时已用 Micrometer 记录提交结果、API 耗时、决策/风险分布和安全降级，并通过 Actuator 查询。所有标签都是有限枚举，避免把保单号或幂等键作为标签造成高基数。生产会再接 Prometheus 和 OpenTelemetry，将 traceId 贯穿 API、工具、模型和数据库，并监控 Token 成本、规则命中和 RAG 质量。

### Q12：真实内部系统不稳定怎么办？

项目已经把工具显式分为 `CRITICAL` 和 `DEGRADABLE`。保单、报价、历史、查勘和规则属于关键链路，失败立即终止；灾害外部数据失败时，`ToolAttempt` 保留失败轨迹，编排器用 `UNKNOWN` 而不是 `LOW` 占位，把资料采集步骤标记为 `DEGRADED`，写入结构化 `DegradationNotice`，并把决策下限提升到人工复核。`degraded-demo` Profile 可以现场复现。生产还要为适配器设置独立超时、舱壁、熔断和限流，并严格区分可降级的上游故障与不可吞掉的程序错误。

### Q13：如何评价 Agent 是否有效？

离线看资料召回、规则一致性、建议完整性和事实引用；在线看平均处理时长、人工采纳率、转人工率、误放行率和赔付表现。Demo 已把核保人结果建模为独立、不可覆盖的 `HumanReview`，确定性计算 `CONFIRMED`、`OVERRIDDEN`、`RESOLVED_MANUAL_REVIEW` 和 `CONTINUED_MANUAL_REVIEW`，并统计结果分布与人工时延。它能形成评测数据入口，但生产上必须先做权限、脱敏、用途审批和样本版本治理，不能直接用原始复核记录训练模型。

### Q14：如果要支持多轮对话怎么做？

会话已有角色消息。生产上按 Token 预算选择历史摘要，事实和规则结果结构化保存，避免每轮重复调用；对会改变承保结论的新事实重新执行规则，并把每次评估关联到会话与版本。

### Q15：为什么报告由后端生成 Markdown，而不是前端拼接或直接生成 PDF？

后端直接接收 `UnderwritingEvaluation` 和可选 `HumanReview`，因此 REST、浏览器和未来批处理可以复用同一口径，也能用单元测试验证中文标签、空数据和 Markdown 转义。Markdown 无需字体和分页依赖，适合代码仓库、评审和面试；前端只绑定下载地址，不复制领域格式。PDF 可以在生产上作为后续渲染层，但不应让演示项目先承担额外复杂度。`GET /api/v1/underwriting/evaluations/{evaluationId}/report` 只读取已保存结果与复核记录，不会重复执行模型或规则。

### Q16：为什么持久化 Demo 选择 H2 和聚合 JSON，而不是直接上 PostgreSQL？

目标是用一个可离线、可自动建表、可真实重启验证的适配器说明 Repository 端口和数据库约束，避免让面试者先安装中间件。表中把主键、关联键、时间和唯一约束结构化，完整不可变聚合保存为 JSON CLOB，既能恢复所有步骤/证据/轨迹，又控制了 Demo 代码量。代价是查询分析能力、迁移治理和多实例能力有限；生产会换 PostgreSQL 的 JSONB/结构化字段、Flyway、事务与 Outbox，不会共享 H2 文件。测试同时断言默认 Profile 选择内存实现、`persistent-demo` 选择 JDBC 实现，并覆盖 JSON 往返和不可覆盖复核。

### Q17：为什么异步任务不直接加一个 `@Async`？

`@Async` 只能把方法扔到另一个线程，不能自动提供任务编号、可查询状态、幂等、容量边界、失败快照和指标。本项目先保存 `PENDING`，工作线程原子推进到 `RUNNING`，最终只允许进入 `SUCCEEDED` 或 `FAILED`；执行器、队列和任务保留数都有上限，同键重试返回原任务，失败只暴露分类后的安全错误。当前仍是单实例调度器：没有持久化队列、租约、心跳、取消、补偿和跨节点抢占。生产长任务会迁移到工作流引擎或数据库任务表加消息队列，而不是简单扩大线程池。

## 容易被追问的取舍

- Hash Embedding 是演示替身，不应包装成生产语义模型。
- 默认仓库是内存实现；可选 H2 Profile 能演示重启恢复，但不是生产数据库架构。
- H2 Profile 尚未覆盖知识库、Prompt 和幂等注册表，也没有生产级迁移、跨聚合事务或多节点协调。
- 异步任务状态和任务幂等仍在单实例内存中；进程退出时不会恢复排队/运行任务，也没有取消与自动补偿。
- 人工复核使用演示人员编号且没有 RBAC/电子签名，不能冒充真实授权审批。
- 规则阈值和条款均为虚构，不代表真实公司规则。
- Agent 自动化的是辅助决策，不是绕过人工授权。

## 面试结束时的总结

“这个 Demo 的核心不是调用一次大模型，而是把资料、知识、规则、模型和审计组织成可替换的工程结构。默认能离线跑通，出现证据缺失或模型异常时也不会突破核保底线；接入真实 Redis、PGVector、企业模型和内部接口后，主流程不需要推倒重写。”
