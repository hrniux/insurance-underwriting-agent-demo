# 中文教学型核保演示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用四组结构化虚构数据、只读场景目录 API 和统一中文教学文档，让初学者能够从业务事实追踪到规则命中与核保结论。

**Architecture:** 新增 `demo` 包作为演示数据边界，由 `DemoScenarioRepository` 从 classpath JSON 加载并校验唯一数据源；现有 `FakeUnderwritingFactTools` 改为从该仓库读取五类事实。`DemoScenarioService` 只负责把领域值转换为中文友好的只读视图，`DemoScenarioController` 暴露目录与详情，不参与核保决策。

**Tech Stack:** Java 21、Spring Boot 4.1、Jackson、Spring MVC、JUnit 5、AssertJ、MockMvc、Bash。

---

### Task 1: 建立结构化场景数据和强校验仓库

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/demo/DemoScenario.java`
- Create: `src/main/java/com/hrniux/underwriting/demo/DemoScenarioRepository.java`
- Create: `src/main/resources/demo/underwriting-scenarios.json`
- Create: `src/test/java/com/hrniux/underwriting/demo/DemoScenarioRepositoryTest.java`

- [ ] **Step 1: 编写加载与校验失败测试**

测试必须覆盖默认资源有四组场景、按保单号排序、重复保单号、子对象保单号不一致和负金额。核心断言：

```java
@Test
void loadsFourSortedScenariosFromTheDefaultResource() {
    DemoScenarioRepository repository = DemoScenarioRepository.loadDefault();
    assertThat(repository.findAll()).extracting(DemoScenario::policyNo)
            .containsExactly("P-1001", "P-2001", "P-3001", "P-4001");
}

@Test
void rejectsDuplicatePolicyNumbers() {
    DemoScenario scenario = validScenario("P-TEST-1");
    assertThatThrownBy(() -> new DemoScenarioRepository(List.of(scenario, scenario)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("重复保单号").hasMessageContaining("P-TEST-1");
}
```

- [ ] **Step 2: 运行测试并确认因类型不存在而失败**

Run: `mvn -Dtest=DemoScenarioRepositoryTest test`

Expected: FAIL，编译器提示 `DemoScenario` 或 `DemoScenarioRepository` 不存在。

- [ ] **Step 3: 定义场景模型**

`DemoScenario` 直接复用现有五类事实 record，并定义预期结果：

```java
public record DemoScenario(
        String policyNo,
        String name,
        String summary,
        String question,
        List<String> learningPoints,
        ExpectedResult expectedResult,
        PolicyFacts policy,
        QuotationFacts quotation,
        UnderwritingHistoryFacts history,
        SurveyReportFacts survey,
        DisasterRiskFacts disaster) {

    public DemoScenario {
        learningPoints = List.copyOf(learningPoints);
    }

    public record ExpectedResult(
            Decision decision,
            RiskLevel riskLevel,
            int riskScore,
            List<String> ruleCodes) {
        public ExpectedResult {
            ruleCodes = List.copyOf(ruleCodes);
        }
    }
}
```

- [ ] **Step 4: 实现场景仓库和启动校验**

仓库公开 Spring 使用的 `ObjectMapper` 构造器、测试用 `loadDefault()`，并将资源解析为不可变、按保单号排序的映射：

```java
@Component
public class DemoScenarioRepository {
    static final String DATA_RESOURCE = "demo/underwriting-scenarios.json";
    private final Map<String, DemoScenario> scenarios;

    public DemoScenarioRepository(ObjectMapper mapper) {
        this(mapper, new ClassPathResource(DATA_RESOURCE));
    }

    DemoScenarioRepository(ObjectMapper mapper, Resource resource) {
        this(read(mapper, resource));
    }

    DemoScenarioRepository(List<DemoScenario> scenarios) {
        this.scenarios = validateAndIndex(scenarios);
    }

    public static DemoScenarioRepository loadDefault() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        return new DemoScenarioRepository(mapper, new ClassPathResource(DATA_RESOURCE));
    }

    public List<DemoScenario> findAll() {
        return List.copyOf(scenarios.values());
    }

    public DemoScenario required(String policyNo) {
        DemoScenario scenario = scenarios.get(policyNo);
        if (scenario == null) {
            throw new ResourceNotFoundException("POLICY_NOT_FOUND", policyNo);
        }
        return scenario;
    }
}
```

`validateAndIndex` 必须落实设计文档中的空列表、重复保单号、五类子对象、保单号一致性、非负数值、日期顺序、风险分范围和规则代码去重校验，失败消息包含具体保单号。

- [ ] **Step 5: 写入四组完整 JSON 数据**

数据必须实现以下确定性结果：

```text
P-1001 -> MANUAL_REVIEW / HIGH / 70 / 4 条规则
P-2001 -> APPROVE       / LOW  / 10 / 0 条规则
P-3001 -> MANUAL_REVIEW / MEDIUM / 40 / RED_RAINSTORM + HIGH_SUM_INSURED
P-4001 -> REJECT        / HIGH / 60 / CRITICAL_FIRE_DEFECT
```

`P-1001`、`P-2001` 保持原值。`P-3001` 使用 1,200 万元保额、红色暴雨、整改完成和无重复出险；`P-4001` 使用 800 万元保额、`CRITICAL_DEFECT`、`EXTREME` 火灾风险、整改完成和无重复出险。

- [ ] **Step 6: 运行数据仓库测试**

Run: `mvn -Dtest=DemoScenarioRepositoryTest test`

Expected: PASS，至少 5 个测试全部通过。

- [ ] **Step 7: 提交数据边界**

```bash
git add src/main/java/com/hrniux/underwriting/demo \
  src/main/resources/demo/underwriting-scenarios.json \
  src/test/java/com/hrniux/underwriting/demo/DemoScenarioRepositoryTest.java
git commit -m "feat: 增加结构化核保演示场景"
```

### Task 2: 让现有工具和规则使用场景仓库

**Files:**
- Modify: `src/main/java/com/hrniux/underwriting/tool/FakeUnderwritingFactTools.java`
- Modify: `src/test/java/com/hrniux/underwriting/tool/FakeUnderwritingFactToolsTest.java`
- Modify: `src/test/java/com/hrniux/underwriting/rule/UnderwritingRuleEngineTest.java`

- [ ] **Step 1: 先为新增场景编写失败测试**

工具测试增加 `P-3001`、`P-4001` 五类事实可查询断言；规则测试精确断言：

```java
@Test
void evaluatesTheMediumAndRejectedTeachingScenarios() {
    RuleEvaluation medium = engine.evaluate(context("P-3001"));
    assertThat(medium.decision()).isEqualTo(Decision.MANUAL_REVIEW);
    assertThat(medium.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    assertThat(medium.riskScore()).isEqualTo(40);
    assertThat(medium.hits()).extracting(RuleResult::code)
            .containsExactly("RED_RAINSTORM", "HIGH_SUM_INSURED");

    RuleEvaluation rejected = engine.evaluate(context("P-4001"));
    assertThat(rejected.decision()).isEqualTo(Decision.REJECT);
    assertThat(rejected.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(rejected.riskScore()).isEqualTo(60);
    assertThat(rejected.hits()).extracting(RuleResult::code)
            .containsExactly("CRITICAL_FIRE_DEFECT");
}
```

测试初始化改为：

```java
facts = new FakeUnderwritingFactTools(DemoScenarioRepository.loadDefault());
```

- [ ] **Step 2: 运行测试并确认构造器不匹配而失败**

Run: `mvn -Dtest=FakeUnderwritingFactToolsTest,UnderwritingRuleEngineTest test`

Expected: FAIL，提示 `FakeUnderwritingFactTools` 尚不接受 `DemoScenarioRepository`。

- [ ] **Step 3: 将工具改为仓库委托**

删除五组硬编码 `Map` 和日期/金额常量，只保留注入仓库与五个委托方法：

```java
@Service
public class FakeUnderwritingFactTools implements UnderwritingFactTools {
    private final DemoScenarioRepository scenarios;

    public FakeUnderwritingFactTools(DemoScenarioRepository scenarios) {
        this.scenarios = Objects.requireNonNull(scenarios);
    }

    public PolicyFacts getPolicy(String policyNo) {
        return scenarios.required(policyNo).policy();
    }

    public QuotationFacts getQuotation(String policyNo) {
        return scenarios.required(policyNo).quotation();
    }

    public UnderwritingHistoryFacts getUnderwritingHistory(String policyNo) {
        return scenarios.required(policyNo).history();
    }

    public SurveyReportFacts getSurveyReport(String policyNo) {
        return scenarios.required(policyNo).survey();
    }

    public DisasterRiskFacts getDisasterRisk(String policyNo) {
        return scenarios.required(policyNo).disaster();
    }
}
```

- [ ] **Step 4: 运行工具、规则和 Agent 回归测试**

Run: `mvn -Dtest=FakeUnderwritingFactToolsTest,UnderwritingRuleEngineTest,UnderwritingAgentOrchestratorTest,UnderwritingMcpToolsTest test`

Expected: PASS；原有 `P-1001`、`P-2001` 与新增场景全部通过。

- [ ] **Step 5: 提交工具迁移**

```bash
git add src/main/java/com/hrniux/underwriting/tool/FakeUnderwritingFactTools.java \
  src/test/java/com/hrniux/underwriting/tool/FakeUnderwritingFactToolsTest.java \
  src/test/java/com/hrniux/underwriting/rule/UnderwritingRuleEngineTest.java
git commit -m "refactor: 统一核保工具的演示数据来源"
```

### Task 3: 增加中文友好的场景目录 API

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/demo/DemoScenarioViews.java`
- Create: `src/main/java/com/hrniux/underwriting/demo/DemoScenarioService.java`
- Create: `src/main/java/com/hrniux/underwriting/demo/DemoScenarioController.java`
- Create: `src/test/java/com/hrniux/underwriting/demo/DemoScenarioServiceTest.java`
- Create: `src/test/java/com/hrniux/underwriting/api/DemoScenarioApiIntegrationTest.java`

- [ ] **Step 1: 编写服务与 API 失败测试**

服务测试断言中文标签和金额显示：

```java
assertThat(service.get("P-1001").expectedResult().decisionLabel()).isEqualTo("人工复核");
assertThat(service.get("P-1001").expectedResult().riskLevelLabel()).isEqualTo("高风险");
assertThat(service.get("P-1001").sumInsuredDisplay()).isEqualTo("2,000 万元");
assertThat(service.get("P-1001").premiumDisplay()).isEqualTo("7 万元");
assertThat(service.get("P-1001").paidLossThreeYearsDisplay()).isEqualTo("120 万元");
```

MockMvc 测试覆盖：

```java
mvc.perform(get("/api/v1/demo/scenarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].policyNo").value("P-1001"))
        .andExpect(jsonPath("$[3].expectedResult.decisionLabel").value("拒保"));

mvc.perform(get("/api/v1/demo/scenarios/P-3001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("暴雨暴露商贸仓库"))
        .andExpect(jsonPath("$.expectedResult.riskScore").value(40))
        .andExpect(jsonPath("$.quotation.policyNo").value("P-3001"));

mvc.perform(get("/api/v1/demo/scenarios/P-MISSING"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("POLICY_NOT_FOUND"));
```

- [ ] **Step 2: 运行测试并确认 API 尚不存在**

Run: `mvn -Dtest=DemoScenarioServiceTest,DemoScenarioApiIntegrationTest test`

Expected: FAIL，编译器或 MockMvc 报告服务/接口不存在。

- [ ] **Step 3: 定义只读响应视图**

`DemoScenarioViews` 包含：

```java
public record ExpectedResultView(
        Decision decision,
        String decisionLabel,
        RiskLevel riskLevel,
        String riskLevelLabel,
        int riskScore,
        List<String> ruleCodes) {}

public record Summary(
        String policyNo,
        String name,
        String summary,
        String question,
        List<String> learningPoints,
        ExpectedResultView expectedResult) {}

public record Detail(
        String policyNo,
        String name,
        String summary,
        String question,
        List<String> learningPoints,
        ExpectedResultView expectedResult,
        String sumInsuredDisplay,
        String premiumDisplay,
        String deductibleDisplay,
        String paidLossThreeYearsDisplay,
        PolicyFacts policy,
        QuotationFacts quotation,
        UnderwritingHistoryFacts history,
        SurveyReportFacts survey,
        DisasterRiskFacts disaster) {}
```

- [ ] **Step 4: 实现中文映射服务**

`DemoScenarioService` 使用穷尽 `switch` 映射：

```java
private String decisionLabel(Decision decision) {
    return switch (decision) {
        case APPROVE -> "自动通过";
        case MANUAL_REVIEW -> "人工复核";
        case REJECT -> "拒保";
    };
}

private String riskLevelLabel(RiskLevel riskLevel) {
    return switch (riskLevel) {
        case LOW -> "低风险";
        case MEDIUM -> "中风险";
        case HIGH -> "高风险";
        case CRITICAL -> "极高风险";
    };
}

private String formatWan(BigDecimal yuan) {
    DecimalFormat format = new DecimalFormat("#,##0.##");
    return format.format(yuan.movePointLeft(4)) + " 万元";
}
```

- [ ] **Step 5: 实现只读控制器**

```java
@RestController
@RequestMapping("/api/v1/demo/scenarios")
public class DemoScenarioController {
    private final DemoScenarioService scenarios;

    public DemoScenarioController(DemoScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    public List<Summary> list() {
        return scenarios.list();
    }

    @GetMapping("/{policyNo}")
    public Detail get(@PathVariable String policyNo) {
        return scenarios.get(policyNo);
    }
}
```

- [ ] **Step 6: 运行服务与接口测试**

Run: `mvn -Dtest=DemoScenarioServiceTest,DemoScenarioApiIntegrationTest test`

Expected: PASS，列表顺序、中文标签、金额展示、详情和 404 全部通过。

- [ ] **Step 7: 提交场景 API**

```bash
git add src/main/java/com/hrniux/underwriting/demo \
  src/test/java/com/hrniux/underwriting/demo/DemoScenarioServiceTest.java \
  src/test/java/com/hrniux/underwriting/api/DemoScenarioApiIntegrationTest.java
git commit -m "feat: 增加中文核保场景目录接口"
```

### Task 4: 建立统一中文教学文档和演示脚本

**Files:**
- Create: `docs/DEMO_DATA_GUIDE.md`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/API_EXAMPLES.md`
- Modify: `scripts/demo.sh`
- Modify: `src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java`

- [ ] **Step 1: 先扩展文档契约测试**

契约测试必须断言：

```java
assertThat(ROOT.resolve("docs/DEMO_DATA_GUIDE.md")).exists().isRegularFile();
assertThat(read("README.md")).contains(
        "十分钟学习路线", "docs/DEMO_DATA_GUIDE.md", "/api/v1/demo/scenarios", "P-3001", "P-4001");
assertThat(read("docs/DEMO_DATA_GUIDE.md")).contains(
        "文档目的", "适用对象", "字段字典", "枚举对照", "虚构数据",
        "P-1001", "P-2001", "P-3001", "P-4001");
assertThat(read("scripts/demo.sh")).contains(
        "服务健康检查", "演示场景目录", "P-1001", "P-2001", "P-3001", "P-4001");
```

- [ ] **Step 2: 运行契约测试并确认失败**

Run: `mvn -Dtest=DocumentationContractTest test`

Expected: FAIL，缺少 `docs/DEMO_DATA_GUIDE.md` 和新入口。

- [ ] **Step 3: 编写教学指南**

指南严格按设计文档十段结构编写，至少包括：

- 四组场景事实与规则推导表；
- 风险分基准 10 分和五条规则的加分/决策说明；
- `policy`、`quotation`、`history`、`survey`、`disaster` 字段字典；
- `Decision`、`RiskLevel`、`HazardLevel` 中英文枚举对照；
- 修改 JSON 后运行 `mvn test` 的步骤与预期结果；
- 不含真实业务数据、不构成核保建议的声明。

- [ ] **Step 4: 更新 README、架构和 API 示例**

README 在快速启动后增加十分钟学习路线；项目结构增加 `demo` 包；场景表扩为四行；进一步阅读链接到教学指南。架构图增加 JSON → `DemoScenarioRepository` → 工具/API。API 示例增加两个 GET 请求及中文标签响应片段。

- [ ] **Step 5: 中文化并扩展演示脚本**

保留健康轮询和失败即退出行为，步骤标题改为：

```text
[1/8] 服务健康检查
[2/8] 演示场景目录
[3/8] RAG 知识检索
[4/8] 共享业务工具
[5/8] 高风险仓库：人工复核（P-1001）
[6/8] 低风险办公楼：自动通过（P-2001）
[7/8] 中风险仓库：人工复核（P-3001）
[8/8] 极端火灾厂房：拒保（P-4001）
```

完成提示必须给出 Swagger、场景目录、教学指南和 MCP 入口。

- [ ] **Step 6: 运行文档契约与 Shell 语法检查**

Run: `mvn -Dtest=DocumentationContractTest test && bash -n scripts/demo.sh`

Expected: PASS，Shell 无语法错误。

- [ ] **Step 7: 提交中文教学体验**

```bash
git add README.md docs/ARCHITECTURE.md docs/API_EXAMPLES.md docs/DEMO_DATA_GUIDE.md \
  scripts/demo.sh src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java
git commit -m "docs: 完善中文核保教学与演示流程"
```

### Task 5: 完整验证与验收审计

**Files:**
- Modify only if verification exposes a defect.

- [ ] **Step 1: 运行完整构建**

Run: `mvn clean verify`

Expected: `BUILD SUCCESS`，所有测试 0 failures、0 errors。

- [ ] **Step 2: 检查格式与预期改动**

Run: `git diff HEAD~3 --check && git status --short && git log --oneline -6`

Expected: `git diff --check` 无输出；工作区干净；日志包含数据、工具迁移、API 和文档提交。

- [ ] **Step 3: 启动应用并进行真实 HTTP 验证**

Run: `mvn spring-boot:run`

另一个终端依次执行：

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/api/v1/demo/scenarios
curl --fail http://localhost:8080/api/v1/demo/scenarios/P-4001
curl --fail -X POST http://localhost:8080/api/v1/underwriting/evaluations \
  -H 'Content-Type: application/json' \
  -d '{"policyNo":"P-3001","question":"这张暴雨风险仓库保单为什么需要人工复核？"}'
bash scripts/demo.sh
```

Expected: 健康状态 `UP`；目录有四组场景；`P-4001` 预期结果为 `REJECT/HIGH/60`；`P-3001` 实际核保为 `MANUAL_REVIEW/MEDIUM/40`；演示脚本八步完成。

- [ ] **Step 4: 对照设计文档逐项审计**

确认：JSON 是唯一数据源、两个 API 可用、四组规则实际结果正确、中文文档互链、脚本运行四场景、原 REST/MCP 兼容、无真实数据或写接口。

- [ ] **Step 5: 提交验证中发现的必要修复**

仅当步骤 1–4 暴露问题时执行：

```bash
git add README.md docs scripts src/main src/test
git commit -m "fix: 修正中文教学演示验收问题"
```

再次运行受影响测试与 `mvn clean verify`，直至全部通过。
