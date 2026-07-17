package com.hrniux.underwriting.demo;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.hrniux.underwriting.demo.DemoScenarioViews.Detail;
import com.hrniux.underwriting.demo.DemoScenarioViews.ExpectedResultView;
import com.hrniux.underwriting.demo.DemoScenarioViews.Summary;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;

@Service
public class DemoScenarioService {

    private final DemoScenarioRepository scenarios;

    public DemoScenarioService(DemoScenarioRepository scenarios) {
        this.scenarios = Objects.requireNonNull(scenarios);
    }

    public List<Summary> list() {
        return scenarios.findAll().stream().map(this::toSummary).toList();
    }

    public Detail get(String policyNo) {
        DemoScenario scenario = scenarios.required(policyNo);
        return new Detail(
                scenario.policyNo(),
                scenario.name(),
                scenario.summary(),
                scenario.question(),
                scenario.learningPoints(),
                toExpectedResult(scenario),
                formatWan(scenario.quotation().sumInsured()),
                formatWan(scenario.quotation().premium()),
                formatWan(scenario.quotation().deductible()),
                formatWan(scenario.history().paidLossThreeYears()),
                scenario.policy(),
                scenario.quotation(),
                scenario.history(),
                scenario.survey(),
                scenario.disaster());
    }

    private Summary toSummary(DemoScenario scenario) {
        return new Summary(
                scenario.policyNo(),
                scenario.name(),
                scenario.summary(),
                scenario.question(),
                scenario.learningPoints(),
                toExpectedResult(scenario));
    }

    private ExpectedResultView toExpectedResult(DemoScenario scenario) {
        var result = scenario.expectedResult();
        return new ExpectedResultView(
                result.decision(),
                decisionLabel(result.decision()),
                result.riskLevel(),
                riskLevelLabel(result.riskLevel()),
                result.riskScore(),
                result.ruleCodes());
    }

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
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(yuan.movePointLeft(4)) + " 万元";
    }
}
