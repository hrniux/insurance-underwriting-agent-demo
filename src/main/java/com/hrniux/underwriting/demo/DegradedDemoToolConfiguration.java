package com.hrniux.underwriting.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.UnderwritingFactTools;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

@Configuration(proxyBeanMethods = false)
@Profile("degraded-demo")
public class DegradedDemoToolConfiguration {

    private static final String DEGRADED_POLICY = "P-2001";

    @Bean
    @Primary
    UnderwritingFactTools degradedDemoTools(DemoScenarioRepository scenarios) {
        return new UnderwritingFactTools() {
            @Override
            public PolicyFacts getPolicy(String policyNo) {
                return scenarios.required(policyNo).policy();
            }

            @Override
            public QuotationFacts getQuotation(String policyNo) {
                return scenarios.required(policyNo).quotation();
            }

            @Override
            public UnderwritingHistoryFacts getUnderwritingHistory(String policyNo) {
                return scenarios.required(policyNo).history();
            }

            @Override
            public SurveyReportFacts getSurveyReport(String policyNo) {
                return scenarios.required(policyNo).survey();
            }

            @Override
            public DisasterRiskFacts getDisasterRisk(String policyNo) {
                DisasterRiskFacts facts = scenarios.required(policyNo).disaster();
                if (DEGRADED_POLICY.equals(policyNo)) {
                    throw new IllegalStateException("simulated disaster platform timeout for degraded demo");
                }
                return facts;
            }
        };
    }
}
