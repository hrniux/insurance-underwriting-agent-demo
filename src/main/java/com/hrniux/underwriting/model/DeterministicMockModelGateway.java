package com.hrniux.underwriting.model;

import java.util.List;
import java.util.stream.Stream;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RuleResult;

public class DeterministicMockModelGateway implements ModelGateway {

    private final String model;

    public DeterministicMockModelGateway(String model) {
        this.model = model;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String decisionText = switch (request.ruleEvaluation().decision()) {
            case APPROVE -> "建议承保";
            case MANUAL_REVIEW -> "建议转人工复核";
            case REJECT -> "建议拒保";
        };
        String warningSummary = request.warnings().isEmpty()
                ? ""
                : "；存在 %d 条数据质量告警，未知风险不得按低风险处理".formatted(request.warnings().size());
        String summary = "%s。确定性规则风险分为 %d，风险等级为 %s%s；本结论仅用于演示辅助决策。"
                .formatted(decisionText, request.ruleEvaluation().riskScore(), request.ruleEvaluation().riskLevel(),
                        warningSummary);
        List<String> reasons = Stream.concat(
                request.ruleEvaluation().hits().stream().map(RuleResult::reason),
                request.warnings().stream()).toList();
        List<String> actions = actionsFor(
                request.ruleEvaluation().decision(), request.evidence().isEmpty(), request.warnings());
        return new ModelResponse(summary, reasons, actions, "mock", model, 1, false, request.prompt());
    }

    private List<String> actionsFor(Decision decision, boolean evidenceMissing, List<String> warnings) {
        if (!warnings.isEmpty()) {
            return List.of("补充并核验缺失的外部灾害风险数据", "由人工核保人员确认完整资料后重新评估");
        }
        if (evidenceMissing) {
            return List.of("补充产品条款与核保规则证据", "提交人工核保人员复核");
        }
        return switch (decision) {
            case APPROVE -> List.of("按标准条款出单", "保留规则校验与知识检索记录");
            case MANUAL_REVIEW -> List.of("补充未完成的风险整改证明", "由高级核保人确认承保条件与免赔额");
            case REJECT -> List.of("记录拒保规则依据", "如风险条件发生实质变化可重新发起评估");
        };
    }
}
