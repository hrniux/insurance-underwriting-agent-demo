# 中文核保 Markdown 报告实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为一次已保存的真实核保评估生成可下载、可测试、便于中文讲解的 Markdown 报告。

**Architecture:** 新增无 Web 依赖的 `UnderwritingMarkdownReportService`，把 `UnderwritingEvaluation` 确定性格式化为 UTF-8 Markdown；现有控制器只负责读取评估与设置下载响应头。演示台在单场景运行成功后绑定报告地址，中文文档同时覆盖浏览器和 curl 使用方式。

**Tech Stack:** Java 21、Spring Boot MVC、JUnit 5、AssertJ、MockMvc、原生 HTML/CSS/JavaScript、Maven

---

## 文件结构

- 新建 `src/main/java/com/hrniux/underwriting/report/UnderwritingMarkdownReportService.java`：集中维护报告结构、中文枚举标签与 Markdown 安全转义。
- 新建 `src/test/java/com/hrniux/underwriting/report/UnderwritingMarkdownReportServiceTest.java`：以完整和空集合两类领域对象验证格式化结果。
- 修改 `src/main/java/com/hrniux/underwriting/api/UnderwritingEvaluationController.java`：增加报告下载端点。
- 修改 `src/test/java/com/hrniux/underwriting/api/UnderwritingApiIntegrationTest.java`：验证真实评估的下载响应和统一 404。
- 修改 `src/main/resources/static/demo/index.html`：增加报告下载操作区。
- 修改 `src/main/resources/static/demo/app.js`：成功时绑定、重置时清理下载链接。
- 修改 `src/main/resources/static/demo/styles.css`：提供可访问、响应式下载按钮样式。
- 修改 `src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java`：锁定前端资源契约。
- 修改 `README.md`、`docs/API_EXAMPLES.md`、`docs/DEMO_DATA_GUIDE.md`、`docs/INTERVIEW_GUIDE.md`：补齐中文使用和讲解说明。
- 修改 `src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java`：锁定报告文档契约。

### Task 1：报告格式化服务

**Files:**
- Create: `src/test/java/com/hrniux/underwriting/report/UnderwritingMarkdownReportServiceTest.java`
- Create: `src/main/java/com/hrniux/underwriting/report/UnderwritingMarkdownReportService.java`

- [ ] **Step 1：编写完整报告与空集合的失败测试**

测试构造固定 `UnderwritingEvaluation`，包含一条规则、一条知识证据、一条步骤轨迹和一条工具轨迹，其中问题和摘录故意包含 `|`、换行和 `<script>`：

```java
@Test
void rendersAReadableChineseReportAndEscapesUntrustedMarkdown() {
    String report = service.render(evaluationWithAuditDetails());

    assertThat(report).contains(
            "# 财险智能核保评估报告",
            "人工复核（`MANUAL_REVIEW`）",
            "高风险（`HIGH`）",
            "| `RED_RAINSTORM` | 高风险（`HIGH`） |",
            "第一行 \\| &lt;script&gt;<br>第二行",
            "理解核保问题（`QUESTION_UNDERSTANDING`）",
            "保单信息工具（`GET_POLICY`）",
            "本报告仅用于技术学习和面试演示");
}

@Test
void rendersExplicitEmptyStatesInsteadOfBlankSections() {
    String report = service.render(evaluationWithoutAuditDetails());

    assertThat(report).contains("## 核保原因\n\n- 无", "## 建议动作\n\n- 无");
    assertThat(report).contains("| 无 | 无 | 无 | 无 | 无 |", "| 无 | 无 | 无 | 无 | 无 | 无 |");
}
```

- [ ] **Step 2：运行测试并确认红灯**

Run: `mvn -Dtest=UnderwritingMarkdownReportServiceTest test`

Expected: FAIL，编译器报告 `UnderwritingMarkdownReportService` 不存在。

- [ ] **Step 3：实现最小且完整的报告服务**

创建 Spring `@Service`，公开唯一入口：

```java
public String render(UnderwritingEvaluation evaluation) {
    Objects.requireNonNull(evaluation, "evaluation must not be null");
    StringBuilder report = new StringBuilder();
    appendHeader(report, evaluation);
    appendDecision(report, evaluation);
    appendList(report, "核保原因", evaluation.reasons());
    appendList(report, "建议动作", evaluation.recommendedActions());
    appendRules(report, evaluation.ruleHits());
    appendEvidence(report, evaluation.evidence());
    appendSteps(report, evaluation.stepTraces());
    appendTools(report, evaluation.toolTraces());
    appendModel(report, evaluation.modelResponse());
    return report.append("## 免责声明\n\n本报告仅用于技术学习和面试演示，不构成真实保险核保意见。报告中的业务数据、规则阈值和条款均为虚构内容。\n")
            .toString();
}
```

使用针对领域枚举的 `switch` 方法返回“中文标签（原始编码）”。所有业务文本先把 `&`、`<`、`>` 转成 HTML 实体，再把反斜杠和表格竖线转义，最后把 CRLF/CR/LF 统一为 `<br>`。表格无数据时追加与列数一致的“无”行，相关度使用 `Locale.ROOT` 格式化为整数百分比。

- [ ] **Step 4：运行报告服务测试并确认绿灯**

Run: `mvn -Dtest=UnderwritingMarkdownReportServiceTest test`

Expected: PASS，2 个测试通过。

- [ ] **Step 5：提交报告服务**

```bash
git add src/main/java/com/hrniux/underwriting/report/UnderwritingMarkdownReportService.java \
  src/test/java/com/hrniux/underwriting/report/UnderwritingMarkdownReportServiceTest.java
git commit -m "feat: 生成中文核保 Markdown 报告"
```

### Task 2：报告下载 API

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/api/UnderwritingApiIntegrationTest.java`
- Modify: `src/main/java/com/hrniux/underwriting/api/UnderwritingEvaluationController.java`

- [ ] **Step 1：编写下载成功和评估不存在的失败测试**

在集成测试中先创建 `P-1001` 评估，再请求报告：

```java
mvc.perform(get("/api/v1/underwriting/evaluations/{id}/report", evaluationId)
        .accept("text/markdown"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/markdown")))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                containsString("underwriting-report-" + evaluationId + ".md")))
        .andExpect(content().string(containsString("# 财险智能核保评估报告")))
        .andExpect(content().string(containsString("`RED_RAINSTORM`")))
        .andExpect(content().string(containsString("本报告仅用于技术学习和面试演示")));

mvc.perform(get("/api/v1/underwriting/evaluations/EVAL-MISSING/report"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("EVALUATION_NOT_FOUND"));
```

- [ ] **Step 2：运行集成测试并确认红灯**

Run: `mvn -Dtest=UnderwritingApiIntegrationTest test`

Expected: FAIL，报告地址返回 404 或无匹配处理器。

- [ ] **Step 3：实现下载端点**

把报告服务注入控制器，并新增：

```java
@GetMapping(value = "/{evaluationId}/report", produces = "text/markdown;charset=UTF-8")
public ResponseEntity<String> report(@PathVariable String evaluationId) {
    UnderwritingEvaluation evaluation = orchestrator.getEvaluation(evaluationId);
    String filename = "underwriting-report-" + evaluation.id() + ".md";
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(reportService.render(evaluation));
}
```

文件名必须来自成功读取后的系统评估编号；不存在时自然进入现有统一异常处理器。

- [ ] **Step 4：运行 API 与报告服务测试**

Run: `mvn -Dtest=UnderwritingMarkdownReportServiceTest,UnderwritingApiIntegrationTest test`

Expected: PASS，报告单元测试和 API 集成测试全部通过。

- [ ] **Step 5：提交下载 API**

```bash
git add src/main/java/com/hrniux/underwriting/api/UnderwritingEvaluationController.java \
  src/test/java/com/hrniux/underwriting/api/UnderwritingApiIntegrationTest.java
git commit -m "feat: 提供核保报告下载接口"
```

### Task 3：演示台一键下载

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java`
- Modify: `src/main/resources/static/demo/index.html`
- Modify: `src/main/resources/static/demo/app.js`
- Modify: `src/main/resources/static/demo/styles.css`

- [ ] **Step 1：先扩展静态资源契约测试**

HTML 断言增加 `id="report-action"`、`id="download-report"` 和“下载中文 Markdown 报告”；JavaScript 断言增加 `configureReportDownload`、`removeAttribute("href")`、`encodeURIComponent(evaluationId)`；CSS 断言增加 `.report-action` 与 `.report-download`。

- [ ] **Step 2：运行静态资源测试并确认红灯**

Run: `mvn -Dtest=DemoConsoleStaticResourceTest test`

Expected: FAIL，页面还没有报告下载元素。

- [ ] **Step 3：增加语义化下载操作区**

在结果摘要之后加入：

```html
<div id="report-action" class="report-action" hidden>
  <a id="download-report" class="report-download">下载中文 Markdown 报告</a>
  <p>报告来自当前已保存评估，不会重复执行模型或规则。</p>
</div>
```

- [ ] **Step 4：绑定并清理下载链接**

新增函数，并在 `renderEvaluation` 末尾调用：

```javascript
function configureReportDownload(evaluationId) {
  const action = find("#report-action");
  const link = find("#download-report");
  const safeId = text(evaluationId, "");
  link.href = `${EVALUATION_API}/${encodeURIComponent(safeId)}/report`;
  link.download = `underwriting-report-${safeId}.md`;
  action.hidden = false;
}
```

`resetEvaluation()` 必须隐藏操作区，并对链接调用 `removeAttribute("href")` 和 `removeAttribute("download")`，确保切换场景时没有陈旧地址。

- [ ] **Step 5：补齐下载按钮样式**

让 `.report-action` 使用浅色边框卡片，`.report-download` 使用与主操作一致的高对比按钮样式；移动端设为块级全宽，保留 `:focus-visible` 现有可访问焦点规则，并支持 `prefers-reduced-motion`。

- [ ] **Step 6：运行静态资源测试并提交**

Run: `mvn -Dtest=DemoConsoleStaticResourceTest test`

Expected: PASS，且客户端仍然不含 `innerHTML`。

```bash
git add src/main/resources/static/demo/index.html src/main/resources/static/demo/app.js \
  src/main/resources/static/demo/styles.css \
  src/test/java/com/hrniux/underwriting/demo/DemoConsoleStaticResourceTest.java
git commit -m "feat: 演示台支持下载中文核保报告"
```

### Task 4：中文文档与假数据讲解

**Files:**
- Modify: `src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java`
- Modify: `README.md`
- Modify: `docs/API_EXAMPLES.md`
- Modify: `docs/DEMO_DATA_GUIDE.md`
- Modify: `docs/INTERVIEW_GUIDE.md`

- [ ] **Step 1：先增加文档契约失败测试**

新增 `documentsTheChineseMarkdownReportFlow()`，断言：README 包含“下载中文 Markdown 报告”；API 示例包含 `/{evaluationId}/report`、`Content-Disposition` 和 `.md`；数据指南包含 `P-1001` 报告阅读顺序和“不会重新执行核保”；面试指南包含“可审计交付物”和 Markdown 报告接口。

- [ ] **Step 2：运行文档契约并确认红灯**

Run: `mvn -Dtest=DocumentationContractTest test`

Expected: FAIL，指出文档尚未描述报告流程。

- [ ] **Step 3：补齐四份中文文档**

- README 浏览器流程在单场景核保后增加下载步骤，并在能力清单说明报告来自已保存评估；
- API 示例提供先创建评估、用 `jq -r .id` 取编号、再使用 `curl --remote-header-name --output-dir .` 下载的完整命令；
- 数据指南以假数据 `P-1001` 解释报告中的“70 分、高风险、人工复核、规则命中、证据和轨迹”如何串联；
- 面试指南把报告作为可审计交付物，说明为什么选择后端 Markdown 而不是浏览器拼接或 PDF。

- [ ] **Step 4：运行文档契约并提交**

Run: `mvn -Dtest=DocumentationContractTest test`

Expected: PASS。

```bash
git add README.md docs/API_EXAMPLES.md docs/DEMO_DATA_GUIDE.md docs/INTERVIEW_GUIDE.md \
  src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java
git commit -m "docs: 补充中文核保报告使用指南"
```

### Task 5：全量验证与真实运行验收

**Files:**
- Verify only

- [ ] **Step 1：静态检查和全量构建**

Run: `git diff --check && mvn clean verify`

Expected: `BUILD SUCCESS`，测试总数至少 82，0 failures，0 errors。

- [ ] **Step 2：启动真实 JAR 并验证下载**

运行 `java -jar target/insurance-underwriting-agent-demo-1.0.0-SNAPSHOT.jar --server.port=18080`，等待健康检查成功；POST `P-1001` 创建评估，再 GET `/report` 保存到临时文件。验证响应包含 `text/markdown` 与附件文件名，文件包含 10 个固定章节、`RED_RAINSTORM`、7 条步骤轨迹、6 条工具轨迹和免责声明。

- [ ] **Step 3：浏览器桌面与移动端验收**

打开 `http://localhost:18080/demo/`：

- 运行 `P-1001`，确认下载入口只在成功后出现，地址包含当前评估编号；
- 切换 `P-2001`，确认旧链接立即隐藏；
- 在 390px 宽度检查无横向溢出、按钮可见且键盘焦点清晰；
- 检查控制台无 JavaScript 错误。

- [ ] **Step 4：核对提交与工作区**

Run: `git status --short --branch && git log --oneline --decorate -8`

Expected: 功能分支工作区干净，提交按报告服务、API、界面、文档分层。

计划执行方式：用户已明确授权全权处理，因此直接使用 `superpowers:executing-plans` 在当前会话逐任务执行，不再暂停询问。
