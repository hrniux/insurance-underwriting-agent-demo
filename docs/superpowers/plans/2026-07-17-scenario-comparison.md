# 全场景横向对比 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有中文演示台增加一键运行四组真实核保并横向比较结论、风险、规则和证据的教学能力。

**Architecture:** 浏览器继续复用场景目录 API 和单次核保 API，按目录顺序执行四次请求，不新增后端批量入口。`app.js` 维护独立批量状态并用安全 DOM API 渲染聚合摘要、成功卡片和失败卡片；单场景状态与批量状态互不覆盖。

**Tech Stack:** Java 21、Spring Boot 4.1、JUnit 5、AssertJ、原生 HTML/CSS/JavaScript、Maven、真实浏览器验收。

---

## 文件职责

- `src/main/resources/static/demo/index.html`：提供批量按钮、状态区和第四步结果容器。
- `src/main/resources/static/demo/app.js`：顺序执行评估、聚合结果、处理部分失败、渲染对比卡片并跳转单场景详情。
- `src/main/resources/static/demo/styles.css`：提供批量操作区、摘要、卡片、失败态和移动端布局。
- `src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java`：锁定页面结构、客户端安全约束和响应式样式契约。
- `src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java`：锁定 README、教学指南和面试指南中的对比使用说明。
- `README.md`：更新首次体验和十分钟路线。
- `docs/DEMO_DATA_GUIDE.md`：说明批量对比的阅读顺序和结果含义。
- `docs/INTERVIEW_GUIDE.md`：把五分钟演示流程调整为浏览器优先。

### Task 1: 增加批量对比页面契约与语义结构

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java`
- Modify: `src/main/resources/static/demo/index.html`

- [ ] **Step 1: 写入失败的 HTML 契约测试**

在 `servesAnAccessibleChineseDemoShell` 的 `contains` 中加入：

```java
"id=\"run-comparison\"",
"id=\"comparison-status\"",
"id=\"comparison-panel\"",
"id=\"comparison-summary\"",
"id=\"comparison-grid\"",
"第四步：横向比较全部场景"
```

- [ ] **Step 2: 运行测试并确认因结构缺失而失败**

Run:

```bash
mvn -Dtest=DemoConsoleStaticResourceTest#servesAnAccessibleChineseDemoShell test
```

Expected: FAIL，输出指出 HTML 不包含 `id="run-comparison"`。

- [ ] **Step 3: 写入最小语义化 HTML**

在场景目录后加入：

```html
<div class="comparison-action">
  <button id="run-comparison" type="button" disabled>对比全部场景</button>
  <p id="comparison-status" aria-live="polite">场景目录加载完成后即可运行横向对比。</p>
</div>
```

在单场景结果区后加入：

```html
<section id="comparison-panel" class="comparison-panel" aria-labelledby="comparison-heading" hidden>
  <h2 id="comparison-heading">第四步：横向比较全部场景</h2>
  <p>四组结果均来自真实核保接口，用于理解风险事实如何改变确定性结论。</p>
  <div id="comparison-summary" class="comparison-summary"></div>
  <div id="comparison-grid" class="comparison-grid"></div>
</section>
```

- [ ] **Step 4: 运行目标测试并确认通过**

Run:

```bash
mvn -Dtest=DemoConsoleStaticResourceTest#servesAnAccessibleChineseDemoShell test
```

Expected: PASS。

- [ ] **Step 5: 提交页面结构**

```bash
git add src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java src/main/resources/static/demo/index.html
git commit -m "feat: 增加全场景对比入口"
```

### Task 2: 以真实核保 API 实现顺序对比与聚合渲染

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java`
- Modify: `src/main/resources/static/demo/app.js`

- [ ] **Step 1: 写入失败的客户端行为契约**

在客户端资源断言中加入：

```java
"comparisonController",
"async function runComparison()",
"for (const scenario of state.scenarios)",
"renderComparisonSummary",
"renderComparisonCard",
"部分场景运行失败",
"查看单场景详情",
"scrollIntoView"
```

保留 `.doesNotContain("innerHTML")`，确保新增渲染继续使用安全 DOM API。

- [ ] **Step 2: 运行测试并确认因客户端行为缺失而失败**

Run:

```bash
mvn -Dtest=DemoConsoleStaticResourceTest#servesAClientThatUsesExistingApisAndSafeDomRendering test
```

Expected: FAIL，输出指出 `comparisonController` 不存在。

- [ ] **Step 3: 增加独立批量状态和按钮状态**

在 `state` 中加入：

```javascript
comparisonController: null,
comparisonResults: []
```

增加按钮状态函数：

```javascript
function setComparisonLoading(loading, completed = 0, total = state.scenarios.length) {
  const button = find("#run-comparison");
  button.disabled = loading || state.scenarios.length === 0;
  button.textContent = loading
    ? `正在对比 ${completed}/${total}……`
    : state.comparisonResults.length
      ? "重新对比全部场景"
      : "对比全部场景";
  if (loading) {
    find("#comparison-status").textContent = `正在顺序运行第 ${Math.min(completed + 1, total)} 组，共 ${total} 组。`;
  }
}
```

- [ ] **Step 4: 增加成功与失败对比项**

```javascript
function comparisonItem(scenario, evaluation, error = null, durationMs = 0) {
  return { scenario, evaluation, error, durationMs };
}

function successfulComparisonItems(items) {
  return items.filter(item => item.evaluation && !item.error);
}

function comparisonDistribution(items) {
  return items.reduce((counts, item) => {
    const decision = item.evaluation?.decision;
    if (decision) counts[decision] = (counts[decision] || 0) + 1;
    return counts;
  }, {});
}
```

- [ ] **Step 5: 增加聚合摘要渲染**

```javascript
function renderComparisonSummary(items) {
  const successful = successfulComparisonItems(items);
  const distribution = comparisonDistribution(successful);
  const scores = successful.map(item => clampScore(item.evaluation.riskScore));
  const matches = successful.filter(item =>
    item.evaluation.decision === item.scenario.expectedResult?.decision
  ).length;
  const ruleCount = successful.reduce((total, item) => total + (item.evaluation.ruleHits?.length || 0), 0);
  const evidenceCount = successful.reduce((total, item) => total + (item.evaluation.evidence?.length || 0), 0);
  const scoreRange = scores.length ? `${Math.min(...scores)}–${Math.max(...scores)} 分` : "暂无";
  const values = [
    ["成功场景", `${successful.length}/${items.length}`],
    ["结论分布", `通过 ${distribution.APPROVE || 0} · 复核 ${distribution.MANUAL_REVIEW || 0} · 拒保 ${distribution.REJECT || 0}`],
    ["符合预期", `${matches}/${successful.length}`],
    ["风险分范围", scoreRange],
    ["规则命中", `${ruleCount} 条`],
    ["知识证据", `${evidenceCount} 条`]
  ];
  setChildren(find("#comparison-summary"), values.map(([title, value]) => {
    const card = el("article", "comparison-metric");
    card.append(el("span", null, title), el("strong", null, value));
    return card;
  }));
}
```

- [ ] **Step 6: 增加成功、失败卡片与查看详情行为**

```javascript
function renderComparisonCard(item) {
  const card = el("article", item.error ? "comparison-card comparison-card--failed" : "comparison-card");
  card.append(
    el("p", "result-card__meta", item.scenario.policyNo),
    el("h3", null, item.scenario.name)
  );
  if (item.error) {
    card.append(
      el("p", "comparison-card__error", `运行失败：${item.error}`),
      el("p", "result-card__meta", "部分场景运行失败，不影响其他场景结果。")
    );
    return card;
  }

  const evaluation = item.evaluation;
  const score = clampScore(evaluation.riskScore);
  const badge = el("p", "decision-badge", label("decisions", evaluation.decision));
  badge.dataset.decision = text(evaluation.decision, "UNKNOWN");
  const matches = evaluation.decision === item.scenario.expectedResult?.decision;
  const progress = el("div", "comparison-risk");
  progress.setAttribute("role", "progressbar");
  progress.setAttribute("aria-label", `${item.scenario.name}风险分`);
  progress.setAttribute("aria-valuemin", "0");
  progress.setAttribute("aria-valuemax", "100");
  progress.setAttribute("aria-valuenow", String(score));
  const fill = el("span", "comparison-risk__fill");
  fill.style.width = `${score}%`;
  progress.append(fill);

  const facts = el("dl", "comparison-facts");
  [
    ["风险等级", label("risks", evaluation.riskLevel)],
    ["风险分", `${score} 分`],
    ["规则命中", `${evaluation.ruleHits?.length || 0} 条`],
    ["知识证据", `${evaluation.evidence?.length || 0} 条`],
    ["评估耗时", `${item.durationMs} ms`]
  ].forEach(([term, value]) => facts.append(el("dt", null, term), el("dd", null, value)));

  const detailButton = el("button", "comparison-card__detail", `查看单场景详情：${item.scenario.name}`);
  detailButton.type = "button";
  detailButton.addEventListener("click", async () => {
    await selectScenario(item.scenario.policyNo);
    find(".detail-panel").scrollIntoView({ behavior: "smooth", block: "start" });
  });
  card.append(
    badge,
    el("p", matches ? "comparison comparison--match" : "comparison comparison--different",
      matches ? "实际结论与预期一致" : `实际结论与预期不同：${label("decisions", item.scenario.expectedResult?.decision)}`),
    progress,
    facts,
    detailButton
  );
  return card;
}
```

- [ ] **Step 7: 增加顺序运行主流程**

```javascript
async function runComparison() {
  if (!state.scenarios.length || state.comparisonController) return;
  const controller = new AbortController();
  state.comparisonController = controller;
  state.comparisonResults = [];
  find("#comparison-panel").hidden = false;
  find("#comparison-summary").replaceChildren();
  find("#comparison-grid").replaceChildren();
  setComparisonLoading(true, 0);

  try {
    for (const scenario of state.scenarios) {
      const startedAt = performance.now();
      try {
        const evaluation = await requestJson(EVALUATION_API, {
          method: "POST",
          signal: controller.signal,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ policyNo: scenario.policyNo, question: scenario.question })
        });
        state.comparisonResults.push(comparisonItem(
          scenario,
          evaluation,
          null,
          Math.max(0, Math.round(performance.now() - startedAt))
        ));
      } catch (error) {
        if (error.name === "AbortError") throw error;
        state.comparisonResults.push(comparisonItem(scenario, null, text(error.message, "未知错误")));
      }
      setComparisonLoading(true, state.comparisonResults.length);
    }

    renderComparisonSummary(state.comparisonResults);
    setChildren(find("#comparison-grid"), state.comparisonResults.map(renderComparisonCard));
    const failed = state.comparisonResults.filter(item => item.error).length;
    find("#comparison-status").textContent = failed
      ? `对比完成：成功 ${state.comparisonResults.length - failed} 组，部分场景运行失败 ${failed} 组。`
      : `对比完成：${state.comparisonResults.length} 组场景均已运行。`;
    find("#comparison-panel").scrollIntoView({ behavior: "smooth", block: "start" });
  } catch (error) {
    if (error.name !== "AbortError") {
      find("#comparison-status").textContent = `对比中断：${text(error.message)}`;
    }
  } finally {
    if (state.comparisonController === controller) {
      state.comparisonController = null;
      setComparisonLoading(false);
    }
  }
}
```

初始化成功后启用批量按钮，并在脚本末尾注册事件：

```javascript
find("#run-comparison").disabled = false;
find("#comparison-status").textContent = `可对比 ${state.scenarios.length} 组虚构场景。`;
find("#run-comparison").addEventListener("click", runComparison);
```

- [ ] **Step 8: 运行目标测试和 JavaScript 语法检查**

Run:

```bash
mvn -Dtest=DemoConsoleStaticResourceTest#servesAClientThatUsesExistingApisAndSafeDomRendering test
node --check src/main/resources/static/demo/app.js
```

Expected: 两个命令都成功，资源测试 PASS，Node 退出码为 0。

- [ ] **Step 9: 提交客户端行为**

```bash
git add src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java src/main/resources/static/demo/app.js
git commit -m "feat: 实现四场景真实核保对比"
```

### Task 3: 增加响应式视觉层与可访问状态

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java`
- Modify: `src/main/resources/static/demo/styles.css`

- [ ] **Step 1: 写入失败的样式契约**

在样式资源断言中加入：

```java
".comparison-action",
".comparison-panel",
".comparison-summary",
".comparison-card",
".comparison-card--failed",
".comparison-risk",
".comparison-card__detail"
```

- [ ] **Step 2: 运行测试并确认因样式缺失而失败**

Run:

```bash
mvn -Dtest=DemoConsoleStaticResourceTest#servesResponsiveAccessibleStylesWithoutExternalAssets test
```

Expected: FAIL，输出指出 `.comparison-action` 不存在。

- [ ] **Step 3: 实现宽屏与基础交互样式**

增加以下完整样式组：

```css
.comparison-action {
  display: grid;
  gap: 0.55rem;
  margin-top: 1.25rem;
  padding-top: 1.1rem;
  border-top: 1px solid var(--line);
}

#run-comparison,
.comparison-card__detail {
  min-height: 2.8rem;
  padding: 0.65rem 1rem;
  color: #fff;
  background: var(--navy);
  border: 0;
  border-radius: 0.7rem;
  font-weight: 800;
  cursor: pointer;
}

#run-comparison:disabled {
  cursor: wait;
  opacity: 0.58;
}

.comparison-panel {
  grid-column: 1 / -1;
  min-width: 0;
  padding: clamp(1.15rem, 2.5vw, 2rem);
  background: rgba(255, 253, 248, 0.98);
  border: 1px solid rgba(16, 47, 56, 0.1);
  border-radius: 1.25rem;
  box-shadow: var(--shadow);
}

.comparison-summary,
.comparison-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 0.85rem;
}

.comparison-summary {
  margin: 1.2rem 0;
}

.comparison-metric,
.comparison-card {
  min-width: 0;
  padding: 1rem;
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 0.85rem;
}

.comparison-metric {
  display: grid;
  gap: 0.15rem;
}

.comparison-metric span,
.comparison-facts dt {
  color: var(--muted);
  font-size: 0.8rem;
}

.comparison-metric strong {
  overflow-wrap: anywhere;
  font-size: 1.05rem;
}

.comparison-card {
  display: flex;
  flex-direction: column;
}

.comparison-card--failed {
  background: var(--reject-soft);
  border-color: #f0b6ad;
}

.comparison-card__error {
  color: var(--reject);
  font-weight: 700;
}

.comparison-risk {
  height: 0.55rem;
  margin: 0.25rem 0 1rem;
  overflow: hidden;
  background: #e4e9e6;
  border-radius: 999px;
}

.comparison-risk__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #1b9a77, #e99a38 58%, #cc3b31);
}

.comparison-facts {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.35rem 0.8rem;
  margin: 0 0 1rem;
}

.comparison-facts dd {
  margin: 0;
  font-weight: 750;
  text-align: right;
}

.comparison-card__detail {
  width: 100%;
  margin-top: auto;
  color: var(--navy);
  background: transparent;
  border: 1px solid var(--navy);
}
```

- [ ] **Step 4: 增加移动端规则并清理重复声明**

在 `@media (max-width: 760px)` 内加入：

```css
.comparison-panel {
  grid-column: auto;
}

.comparison-summary,
.comparison-grid {
  grid-template-columns: 1fr;
}
```

在 `@media (max-width: 470px)` 的面板列表中加入 `.comparison-panel`，并删除 `.decision-badge` 中重复的 `font-weight` 声明。

- [ ] **Step 5: 运行目标测试**

Run:

```bash
mvn -Dtest=DemoConsoleStaticResourceTest#servesResponsiveAccessibleStylesWithoutExternalAssets test
```

Expected: PASS。

- [ ] **Step 6: 提交响应式样式**

```bash
git add src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java src/main/resources/static/demo/styles.css
git commit -m "style: 完善全场景对比响应式布局"
```

### Task 4: 更新中文教学和面试文档

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java`
- Modify: `README.md`
- Modify: `docs/DEMO_DATA_GUIDE.md`
- Modify: `docs/INTERVIEW_GUIDE.md`

- [ ] **Step 1: 写入失败的文档契约**

新增测试：

```java
@Test
void documentsTheRealFourScenarioComparisonFlow() throws IOException {
    assertThat(read("README.md")).contains(
            "对比全部场景",
            "自动通过 1、人工复核 2、拒保 1");
    assertThat(read("docs/DEMO_DATA_GUIDE.md")).contains(
            "横向对比",
            "风险分范围",
            "部分场景");
    assertThat(read("docs/INTERVIEW_GUIDE.md")).contains(
            "浏览器演示台",
            "对比全部场景",
            "实际结论与预期一致");
}
```

- [ ] **Step 2: 运行测试并确认因文档缺失而失败**

Run:

```bash
mvn -Dtest=DocumentationContractTest#documentsTheRealFourScenarioComparisonFlow test
```

Expected: FAIL，输出指出 README 不包含“对比全部场景”。

- [ ] **Step 3: 更新 README**

在“浏览器交互演示”步骤中加入：

```markdown
4. 点击“对比全部场景”，让四组虚构保单顺序调用同一个真实核保接口；
5. 从对比总览确认自动通过 1、人工复核 2、拒保 1，风险分范围为 10–70，再进入任一场景查看五类事实。

横向对比不会读取预计算静态结论：四张卡片都来自实时 `POST /api/v1/underwriting/evaluations`。单组请求失败时，页面会保留其他成功结果并显示独立失败卡片。
```

把十分钟学习路线第 3 步改为：

```markdown
3. 打开 `/demo/`，先点击“对比全部场景”理解结论分布，再进入一组场景运行详细核保。
```

- [ ] **Step 4: 更新虚构数据指南**

在浏览器学习路线中加入：

```markdown
#### 全场景横向对比

点击“对比全部场景”后，页面按 `P-1001` 至 `P-4001` 的目录顺序调用四次真实核保接口。建议按以下顺序阅读：

1. 先看结论分布，确认自动通过 1、人工复核 2、拒保 1；
2. 再看风险分范围 10–70，以及每组规则命中数和知识证据数；
3. 最后点击“查看单场景详情”，回到五类业务事实解释差异来源。

如果某组请求失败，其他场景仍会继续运行。失败卡片表示技术请求未完成，不表示业务结论为拒保，也不能作为真实承保判断。
```

- [ ] **Step 5: 更新面试指南**

把五分钟演示流程的主体替换为：

```markdown
### 第 0–1 分钟：说明边界并打开浏览器演示台

启动应用后打开 <http://localhost:8080/demo/>。说明 Agent 负责资料聚合、规则检查和建议整理，确定性规则拥有决策底线；页面全部数据均为虚构数据。

### 第 1–2 分钟：横向比较四组真实结果

点击“对比全部场景”，说明页面顺序调用四次真实核保接口，不展示预计算截图。总览应显示自动通过 1、人工复核 2、拒保 1、风险分 10–70，四组实际结论与预期一致。

### 第 2–3 分钟：展开一组规则、RAG 与轨迹

进入 `P-1001`，点击“运行智能核保”，展示 70 分、四条规则、四条知识证据、七步 Agent 轨迹和六类工具调用，并说明规则结果不能被模型文本降低。

### 第 3–4 分钟：说明 REST、MCP 与模型边界

指出演示台、REST 调试接口和六个真实 `@McpTool` 复用同一套工具与规则逻辑；默认 Mock 无需 API Key，OpenAI-compatible 网关负责超时、重试和显式降级。

### 第 4–5 分钟：讲可靠性与生产化

说明失败响应、步骤/工具耗时、模型 provider 与 fallback 标记如何支持审计，再给出 Redis、PostgreSQL/PGVector、真实内部 API、企业模型和 OpenTelemetry 的替换路径。
```

- [ ] **Step 6: 运行文档契约并提交**

Run:

```bash
mvn -Dtest=DocumentationContractTest test
```

Expected: 全部 `DocumentationContractTest` PASS。

```bash
git add README.md docs/DEMO_DATA_GUIDE.md docs/INTERVIEW_GUIDE.md src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java
git commit -m "docs: 增加全场景对比演示说明"
```

### Task 5: 完整验证与交付清理

**Files:**
- Verify all modified files

- [ ] **Step 1: 执行静态检查和全量 Maven 验证**

Run:

```bash
node --check src/main/resources/static/demo/app.js
git diff --check
mvn clean verify
```

Expected: Node 和 Git 检查退出码为 0；Maven 输出 `BUILD SUCCESS`，测试失败数和错误数均为 0。

- [ ] **Step 2: 启动打包应用并做真实 HTTP 冒烟**

Run:

```bash
java -jar target/insurance-underwriting-agent-demo-1.0.0-SNAPSHOT.jar
```

在另一终端确认 `/actuator/health` 为 `UP`、`/demo/` 包含“第四步：横向比较全部场景”、场景目录包含四组数据，并对四组保单逐个 POST 评估。预期决策和风险分分别为：

```text
P-1001 MANUAL_REVIEW 70
P-2001 APPROVE 10
P-3001 MANUAL_REVIEW 40
P-4001 REJECT 60
```

- [ ] **Step 3: 做真实浏览器验收**

在桌面视口点击“对比全部场景”，确认：

```text
成功场景 4/4
结论分布 通过 1 · 复核 2 · 拒保 1
符合预期 4/4
风险分范围 10–70 分
四张成功卡片
```

点击 `P-4001` 的“查看单场景详情”，确认场景切换且单场景结果保持待运行。把视口调整为 390×844，确认 `document.documentElement.scrollWidth === window.innerWidth`；检查控制台无 JavaScript 错误。

- [ ] **Step 4: 停止应用并确认端口释放**

停止 JAR 进程后运行：

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN || true
```

Expected: 无输出。

- [ ] **Step 5: 检查分支状态并准备合并**

Run:

```bash
git status --short --branch
git log --oneline --decorate -5
```

Expected: 功能分支工作区干净，提交历史包含页面、行为、样式和文档四类提交。
