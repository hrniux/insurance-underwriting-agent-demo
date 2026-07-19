package com.hrniux.underwriting.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class DocumentationContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void shipsAllOperatorAndInterviewDeliverables() throws IOException {
        List<String> files = List.of(
                "README.md",
                "docs/ARCHITECTURE.md",
                "docs/API_EXAMPLES.md",
                "docs/DEMO_DATA_GUIDE.md",
                "docs/INTERVIEW_GUIDE.md",
                "scripts/demo.sh",
                "src/main/resources/static/demo/index.html",
                "src/main/resources/static/demo/app.js",
                "src/main/resources/static/demo/styles.css",
                "Dockerfile",
                ".dockerignore");

        assertThat(files).allSatisfy(file -> assertThat(ROOT.resolve(file)).exists().isRegularFile());
    }

    @Test
    void readmeCoversStartupCoreCapabilitiesScenariosAndEvolution() throws IOException {
        String readme = read("README.md");

        assertThat(readme).contains(
                "Java 21",
                "无需 API Key",
                "模型切换",
                "RAG",
                "MCP",
                "P-1001",
                "P-2001",
                "P-3001",
                "P-4001",
                "十分钟学习路线",
                "docs/DEMO_DATA_GUIDE.md",
                "/api/v1/demo/scenarios",
                "/demo/",
                "中文智能核保演示台",
                "生产化演进",
                "mvn clean verify",
                "mvn spring-boot:run",
                "bash scripts/demo.sh");
    }

    @Test
    void apiExamplesCoverEveryPublicEndpointGroup() throws IOException {
        String examples = read("docs/API_EXAMPLES.md");

        assertThat(examples).contains(
                "/api/v1/sessions",
                "/api/v1/underwriting/evaluations",
                "/api/v1/knowledge/documents",
                "/api/v1/knowledge/search",
                "/api/v1/prompts",
                "/api/v1/tools",
                "/api/v1/demo/scenarios",
                "/actuator/health",
                "/v3/api-docs",
                "/swagger-ui/index.html",
                "/mcp");
    }

    @Test
    void interviewGuideProvidesAFiveMinuteFlowAndAtLeastTenQuestions() throws IOException {
        String guide = read("docs/INTERVIEW_GUIDE.md");

        assertThat(guide).contains("五分钟演示流程", "常见面试问题");
        long questionCount = guide.lines().filter(line -> line.matches("### Q\\d+.*")).count();
        assertThat(questionCount).isGreaterThanOrEqualTo(10);
    }

    @Test
    void teachingGuideExplainsAllScenariosFieldsEnumsAndTheFictionalBoundary() throws IOException {
        String guide = read("docs/DEMO_DATA_GUIDE.md");

        assertThat(guide).contains(
                "文档目的",
                "适用对象",
                "十分钟学习路线",
                "字段字典",
                "枚举对照",
                "如何修改或新增场景",
                "虚构数据",
                "P-1001",
                "P-2001",
                "P-3001",
                "P-4001",
                "RED_RAINSTORM",
                "CRITICAL_FIRE_DEFECT");
    }

    @Test
    void architectureAndDemoScriptDescribeTheSharedScenarioSourceAndChineseFlow() throws IOException {
        assertThat(read("docs/ARCHITECTURE.md")).contains(
                "DemoScenarioRepository",
                "underwriting-scenarios.json",
                "/api/v1/demo/scenarios");

        assertThat(read("scripts/demo.sh")).contains(
                "服务健康检查",
                "演示场景目录",
                "RAG 知识检索",
                "共享业务工具",
                "${title}（${policy_no}）",
                "P-1001",
                "P-2001",
                "P-3001",
                "P-4001");
        assertThat(ROOT.resolve("scripts/demo.sh")).isExecutable();
    }

    @Test
    void documentsTheInteractiveDemoConsoleAndItsFictionalDataBoundary() throws IOException {
        assertThat(read("README.md")).contains(
                "http://localhost:8080/demo/",
                "中文智能核保演示台",
                "虚构数据");
        assertThat(read("docs/DEMO_DATA_GUIDE.md")).contains(
                "浏览器交互演示",
                "选择演示场景",
                "运行智能核保",
                "规则命中",
                "七步");
        assertThat(read("docs/ARCHITECTURE.md")).contains(
                "/demo/",
                "静态演示层",
                "/api/v1/underwriting/evaluations");
    }

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

    @Test
    void documentsTheChineseMarkdownReportFlow() throws IOException {
        assertThat(read("README.md")).contains(
                "下载中文 Markdown 报告",
                "不会重新执行模型或规则");
        assertThat(read("docs/API_EXAMPLES.md")).contains(
                "/{evaluationId}/report",
                "Content-Disposition",
                ".md");
        assertThat(read("docs/DEMO_DATA_GUIDE.md")).contains(
                "P-1001 报告阅读顺序",
                "不会重新执行核保");
        assertThat(read("docs/INTERVIEW_GUIDE.md")).contains(
                "可审计交付物",
                "GET /api/v1/underwriting/evaluations/{evaluationId}/report");
    }

    @Test
    void documentsIdempotentSubmissionAndOperationalMetrics() throws IOException {
        assertThat(read("README.md")).contains(
                "Idempotency-Key",
                "Idempotency-Replayed",
                "IDEMPOTENCY_KEY_CONFLICT",
                "underwriting.evaluation.submissions");
        assertThat(read("docs/ARCHITECTURE.md")).contains(
                "CompletableFuture",
                "underwriting.evaluation.duration",
                "risk_level");
        assertThat(read("docs/API_EXAMPLES.md")).contains(
                "outcome=created|replayed|conflict|failed");
        assertThat(read("scripts/demo.sh")).contains(
                "Idempotency-Key: demo-evaluation-${policy_no}");
    }

    private String read(String path) throws IOException {
        return Files.readString(ROOT.resolve(path));
    }
}
