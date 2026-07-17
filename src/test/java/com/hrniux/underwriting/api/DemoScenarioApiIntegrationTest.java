package com.hrniux.underwriting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class DemoScenarioApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void listsFourSortedScenarioSummariesWithChineseLabels() throws Exception {
        mvc.perform(get("/api/v1/demo/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].policyNo").value("P-1001"))
                .andExpect(jsonPath("$[0].expectedResult.decision").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$[0].expectedResult.decisionLabel").value("人工复核"))
                .andExpect(jsonPath("$[3].policyNo").value("P-4001"))
                .andExpect(jsonPath("$[3].expectedResult.decisionLabel").value("拒保"));
    }

    @Test
    void returnsFullScenarioDetailWithoutRunningAnEvaluation() throws Exception {
        mvc.perform(get("/api/v1/demo/scenarios/P-3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("暴雨暴露商贸仓库"))
                .andExpect(jsonPath("$.expectedResult.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.expectedResult.riskScore").value(40))
                .andExpect(jsonPath("$.sumInsuredDisplay").value("1,200 万元"))
                .andExpect(jsonPath("$.policy.policyNo").value("P-3001"))
                .andExpect(jsonPath("$.quotation.policyNo").value("P-3001"))
                .andExpect(jsonPath("$.history.policyNo").value("P-3001"))
                .andExpect(jsonPath("$.survey.policyNo").value("P-3001"))
                .andExpect(jsonPath("$.disaster.policyNo").value("P-3001"));
    }

    @Test
    void returnsTheStandardProblemDetailsForAnUnknownScenario() throws Exception {
        mvc.perform(get("/api/v1/demo/scenarios/P-MISSING").header("X-Trace-Id", "demo-missing-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("demo-missing-001"));
    }
}
