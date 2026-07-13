package com.hrniux.underwriting.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.api.ApiDtos.ToolInvokeRequest;
import com.hrniux.underwriting.rule.UnderwritingContext;
import com.hrniux.underwriting.rule.UnderwritingRuleEngine;
import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.ToolInvocation;
import com.hrniux.underwriting.tool.ToolName;
import com.hrniux.underwriting.tool.ToolRegistry;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final ToolRegistry tools;
    private final UnderwritingRuleEngine rules;

    public ToolController(ToolRegistry tools, UnderwritingRuleEngine rules) {
        this.tools = tools;
        this.rules = rules;
    }

    @GetMapping
    public List<ToolName> list() {
        return tools.toolNames();
    }

    @PostMapping("/{toolName}/invoke")
    public ToolInvocation<?> invoke(
            @PathVariable ToolName toolName,
            @Valid @RequestBody ToolInvokeRequest request) {
        if (toolName != ToolName.VALIDATE_RULES) {
            return tools.invoke(toolName, request.policyNo());
        }
        String policyNo = request.policyNo();
        UnderwritingContext context = new UnderwritingContext(
                (PolicyFacts) tools.invoke(ToolName.GET_POLICY, policyNo).result(),
                (QuotationFacts) tools.invoke(ToolName.GET_QUOTATION, policyNo).result(),
                (UnderwritingHistoryFacts) tools.invoke(ToolName.GET_UNDERWRITING_HISTORY, policyNo).result(),
                (SurveyReportFacts) tools.invoke(ToolName.GET_SURVEY_REPORT, policyNo).result(),
                (DisasterRiskFacts) tools.invoke(ToolName.GET_DISASTER_RISK, policyNo).result());
        return tools.invokeRuleValidation(policyNo, () -> rules.evaluate(context));
    }
}
