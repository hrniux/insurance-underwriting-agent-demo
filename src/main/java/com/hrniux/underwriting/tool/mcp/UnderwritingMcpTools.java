package com.hrniux.underwriting.tool.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.rule.RuleEvaluation;
import com.hrniux.underwriting.rule.UnderwritingContext;
import com.hrniux.underwriting.rule.UnderwritingRuleEngine;
import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.ToolName;
import com.hrniux.underwriting.tool.ToolRegistry;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

@Service
public class UnderwritingMcpTools {

    private final ToolRegistry tools;
    private final UnderwritingRuleEngine rules;

    public UnderwritingMcpTools(ToolRegistry tools, UnderwritingRuleEngine rules) {
        this.tools = tools;
        this.rules = rules;
    }

    @McpTool(
            name = "get_policy",
            description = "查询虚构财险保单与标的信息",
            generateOutputSchema = true)
    public PolicyFacts getPolicy(
            @McpToolParam(description = "保单号，例如 P-1001", required = true) String policyNo) {
        return (PolicyFacts) tools.invoke(ToolName.GET_POLICY, policyNo).result();
    }

    @McpTool(
            name = "get_quotation",
            description = "查询虚构财险报价、保额、费率、保费与免赔额",
            generateOutputSchema = true)
    public QuotationFacts getQuotation(
            @McpToolParam(description = "保单号，例如 P-1001", required = true) String policyNo) {
        return (QuotationFacts) tools.invoke(ToolName.GET_QUOTATION, policyNo).result();
    }

    @McpTool(
            name = "get_underwriting_history",
            description = "查询虚构历史核保结论与近三年赔付记录",
            generateOutputSchema = true)
    public UnderwritingHistoryFacts getUnderwritingHistory(
            @McpToolParam(description = "保单号，例如 P-1001", required = true) String policyNo) {
        return (UnderwritingHistoryFacts) tools.invoke(ToolName.GET_UNDERWRITING_HISTORY, policyNo).result();
    }

    @McpTool(
            name = "get_survey_report",
            description = "查询虚构风险查勘结论、整改状态和未决问题",
            generateOutputSchema = true)
    public SurveyReportFacts getSurveyReport(
            @McpToolParam(description = "保单号，例如 P-1001", required = true) String policyNo) {
        return (SurveyReportFacts) tools.invoke(ToolName.GET_SURVEY_REPORT, policyNo).result();
    }

    @McpTool(
            name = "get_disaster_risk",
            description = "查询虚构暴雨、洪水和火灾灾害风险等级",
            generateOutputSchema = true)
    public DisasterRiskFacts getDisasterRisk(
            @McpToolParam(description = "保单号，例如 P-1001", required = true) String policyNo) {
        return (DisasterRiskFacts) tools.invoke(ToolName.GET_DISASTER_RISK, policyNo).result();
    }

    @McpTool(
            name = "validate_rules",
            description = "汇总保单资料并执行确定性财险核保规则校验",
            generateOutputSchema = true)
    public RuleEvaluation validateRules(
            @McpToolParam(description = "保单号，例如 P-1001", required = true) String policyNo) {
        UnderwritingContext context = new UnderwritingContext(
                getPolicy(policyNo),
                getQuotation(policyNo),
                getUnderwritingHistory(policyNo),
                getSurveyReport(policyNo),
                getDisasterRisk(policyNo));
        return tools.invokeRuleValidation(policyNo, () -> rules.evaluate(context)).result();
    }
}
