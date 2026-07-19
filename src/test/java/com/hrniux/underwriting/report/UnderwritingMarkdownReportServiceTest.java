package com.hrniux.underwriting.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.agent.AgentStep;
import com.hrniux.underwriting.agent.DegradationNotice;
import com.hrniux.underwriting.agent.Evidence;
import com.hrniux.underwriting.agent.StepStatus;
import com.hrniux.underwriting.agent.StepTrace;
import com.hrniux.underwriting.agent.UnderwritingEvaluation;
import com.hrniux.underwriting.model.ModelResponse;
import com.hrniux.underwriting.rag.DocumentType;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.rule.RuleResult;
import com.hrniux.underwriting.rule.RuleSeverity;
import com.hrniux.underwriting.tool.ToolCallStatus;
import com.hrniux.underwriting.tool.ToolCallTrace;
import com.hrniux.underwriting.tool.ToolName;

class UnderwritingMarkdownReportServiceTest {

    private final UnderwritingMarkdownReportService service = new UnderwritingMarkdownReportService();

    @Test
    void rendersAReadableChineseReportAndEscapesUntrustedMarkdown() {
        String report = service.render(evaluationWithAuditDetails());

        assertThat(report).contains(
                "# 财险智能核保评估报告",
                "人工复核（`MANUAL_REVIEW`）",
                "高风险（`HIGH`）",
                "## 数据质量与安全降级",
                "`NON_CRITICAL_TOOL_UNAVAILABLE`",
                "灾害风险工具（`GET_DISASTER_RISK`）",
                "人工复核（`MANUAL_REVIEW`）",
                "| `RED_RAINSTORM` | 高风险（`HIGH`） |",
                "第一行 \\| &lt;script&gt;<br>第二行",
                "理解核保问题（`QUESTION_UNDERSTANDING`）",
                "保单信息工具（`GET_POLICY`）",
                "## 模型执行信息",
                "本报告仅用于技术学习和面试演示");
    }

    @Test
    void rendersExplicitEmptyStatesInsteadOfBlankSections() {
        String report = service.render(evaluationWithoutAuditDetails());

        assertThat(report).contains(
                "## 核保原因\n\n- 无",
                "## 建议动作\n\n- 无",
                "本次评估未发生数据源降级",
                "| 无 | 无 | 无 | 无 | 无 |",
                "| 无 | 无 | 无 | 无 | 无 | 无 |");
    }

    private UnderwritingEvaluation evaluationWithAuditDetails() {
        Instant timestamp = Instant.parse("2026-07-17T05:30:00Z");
        return new UnderwritingEvaluation(
                "EVAL-REPORT-1",
                "SES-REPORT-1",
                "P-1001",
                "这张保单是否承保 | <script>？\n请说明原因。",
                Decision.MANUAL_REVIEW,
                RiskLevel.HIGH,
                70,
                "规则要求人工复核。",
                List.of("暴雨风险较高"),
                List.of("核实排水整改情况"),
                List.of(new DegradationNotice(
                        "NON_CRITICAL_TOOL_UNAVAILABLE",
                        ToolName.GET_DISASTER_RISK,
                        "TOOL_CALL_FAILED",
                        "灾害风险数据暂时不可用，必须转人工补充核验。",
                        Decision.MANUAL_REVIEW)),
                List.of(new Evidence(
                        "DOC-001",
                        "CHUNK-001",
                        "暴雨风险指引",
                        DocumentType.RISK_GUIDE,
                        "第一行 | <script>\n第二行",
                        0.86)),
                List.of(new RuleResult(
                        "RED_RAINSTORM",
                        "暴雨红色预警触发人工复核",
                        RuleSeverity.HIGH,
                        30,
                        Decision.MANUAL_REVIEW,
                        100)),
                List.of(new ToolCallTrace(
                        ToolName.GET_POLICY,
                        timestamp,
                        12,
                        ToolCallStatus.SUCCESS,
                        "policyNo=P-1001",
                        "已返回保单事实",
                        null)),
                List.of(new StepTrace(
                        AgentStep.QUESTION_UNDERSTANDING,
                        StepStatus.SUCCESS,
                        timestamp,
                        8,
                        null)),
                new ModelResponse(
                        "规则要求人工复核。",
                        List.of("暴雨风险较高"),
                        List.of("核实排水整改情况"),
                        "mock",
                        "deterministic-underwriting-mock",
                        1,
                        false),
                timestamp);
    }

    private UnderwritingEvaluation evaluationWithoutAuditDetails() {
        return new UnderwritingEvaluation(
                "EVAL-EMPTY-1",
                "SES-EMPTY-1",
                "P-2001",
                "是否承保？",
                Decision.APPROVE,
                RiskLevel.LOW,
                10,
                "基础风险较低。",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ModelResponse(
                        "基础风险较低。",
                        List.of(),
                        List.of(),
                        "mock",
                        "deterministic-underwriting-mock",
                        1,
                        false),
                Instant.parse("2026-07-17T05:31:00Z"));
    }
}
