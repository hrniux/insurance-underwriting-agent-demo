package com.hrniux.underwriting.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@ActiveProfiles("degraded-demo")
class DegradedUnderwritingApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void returnsAndReportsASafeManualReviewWhenTheDisasterToolIsUnavailable() throws Exception {
        String response = mvc.perform(post("/api/v1/underwriting/evaluations")
                        .header("Idempotency-Key", "degraded-api-integration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNo":"P-2001","question":"灾害平台不可用时是否可以自动承保？"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.riskScore").value(10))
                .andExpect(jsonPath("$.degradations[0].code").value("NON_CRITICAL_TOOL_UNAVAILABLE"))
                .andExpect(jsonPath("$.degradations[0].toolName").value("GET_DISASTER_RISK"))
                .andExpect(jsonPath("$.degradations[0].decisionFloor").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.summary").value(containsString("未知风险不得按低风险处理")))
                .andExpect(jsonPath("$.reasons[0]").value(containsString("灾害风险数据暂时不可用")))
                .andExpect(jsonPath("$.recommendedActions[0]").value("补充并核验缺失的外部灾害风险数据"))
                .andExpect(jsonPath("$.toolTraces[4].status").value("FAILED"))
                .andExpect(jsonPath("$.toolTraces[4].toolName").value("GET_DISASTER_RISK"))
                .andExpect(jsonPath("$.stepTraces[1].status").value("DEGRADED"))
                .andExpect(jsonPath("$.stepTraces[1].errorCode").value("NON_CRITICAL_TOOL_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        String evaluationId = JsonPath.read(response, "$.id");

        mvc.perform(get("/api/v1/underwriting/evaluations/{id}/report", evaluationId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("## 数据质量与安全降级")))
                .andExpect(content().string(containsString("`NON_CRITICAL_TOOL_UNAVAILABLE`")))
                .andExpect(content().string(containsString("降级完成（`DEGRADED`）")));

        mvc.perform(get("/actuator/metrics/underwriting.agent.degradations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'tool')]").exists())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'reason')]").exists());
    }

}
