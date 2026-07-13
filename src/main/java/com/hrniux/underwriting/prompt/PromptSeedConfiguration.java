package com.hrniux.underwriting.prompt;

import java.util.Set;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PromptSeedConfiguration {

    @Bean
    ApplicationRunner seedUnderwritingPrompt(PromptTemplateService service) {
        return arguments -> {
            if (!service.versions("underwriting-analysis").isEmpty()) {
                return;
            }
            service.createVersion(
                    "underwriting-analysis",
                    """
                    你是财险智能核保助手。请依据确定性规则和引用证据生成可解释建议，不得降低规则给出的决策下限。

                    用户问题：{{question}}
                    保单事实：{{policyFacts}}
                    报价事实：{{quotationFacts}}
                    历史事实：{{historyFacts}}
                    查勘事实：{{surveyFacts}}
                    灾害事实：{{disasterFacts}}
                    规则结果：{{ruleResults}}
                    知识证据：{{knowledgeEvidence}}
                    """.trim(),
                    Set.of(
                            "question",
                            "policyFacts",
                            "quotationFacts",
                            "historyFacts",
                            "surveyFacts",
                            "disasterFacts",
                            "ruleResults",
                            "knowledgeEvidence"));
        };
    }
}
