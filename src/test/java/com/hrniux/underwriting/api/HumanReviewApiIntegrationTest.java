package com.hrniux.underwriting.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class HumanReviewApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void closesTheLoopThroughApiReportExportAndMetrics() throws Exception {
        String evaluationJson = createEvaluation("P-1001", "复核高风险仓库是否可以有条件承保？");
        String evaluationId = JsonPath.read(evaluationJson, "$.id");
        String reviewPath = "/api/v1/underwriting/evaluations/" + evaluationId + "/review";

        String reviewJson = mvc.perform(post(reviewPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId":"UW-DEMO-001",
                                  "outcome":"APPROVED",
                                  "comment":"整改证明已由人工核验，同意附条件承保。",
                                  "conditions":["提高免赔额至 10 万元","每季度复查排水设施"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, reviewPath))
                .andExpect(jsonPath("$.id").value(containsString("REVIEW-")))
                .andExpect(jsonPath("$.evaluationId").value(evaluationId))
                .andExpect(jsonPath("$.reviewerId").value("UW-DEMO-001"))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.relationship").value("RESOLVED_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.conditions.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String reviewId = JsonPath.read(reviewJson, "$.id");

        mvc.perform(get(reviewPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId));

        mvc.perform(get("/api/v1/underwriting/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(reviewId)).exists());

        mvc.perform(get("/api/v1/underwriting/evaluations/{id}/report", evaluationId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("## 人工复核闭环")))
                .andExpect(content().string(containsString("Agent 辅助建议")))
                .andExpect(content().string(containsString("同意承保（`APPROVED`）")))
                .andExpect(content().string(containsString("完成人工复核（`RESOLVED_MANUAL_REVIEW`）")))
                .andExpect(content().string(containsString("提高免赔额至 10 万元")));

        mvc.perform(get("/actuator/metrics/underwriting.human.reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'outcome')]").exists())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'relationship')]").exists());
        mvc.perform(get("/actuator/metrics/underwriting.human.review.delay"))
                .andExpect(status().isOk());

        mvc.perform(post(reviewPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId":"UW-DEMO-002",
                                  "outcome":"REJECTED",
                                  "comment":"尝试覆盖已经存在的复核结论",
                                  "conditions":[]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HUMAN_REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void validatesReviewInputAndReportsMissingResourcesPrecisely() throws Exception {
        mvc.perform(post("/api/v1/underwriting/evaluations/EVAL-MISSING/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerId":"UW-DEMO-001",
                                  "outcome":"REJECTED",
                                  "comment":"不存在的评估",
                                  "conditions":[]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVALUATION_NOT_FOUND"));

        String evaluationId = JsonPath.read(
                createEvaluation("P-2001", "创建一条尚未复核的评估。"), "$.id");
        String reviewPath = "/api/v1/underwriting/evaluations/" + evaluationId + "/review";

        mvc.perform(get(reviewPath))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HUMAN_REVIEW_NOT_FOUND"));

        mvc.perform(post(reviewPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"","outcome":null,"comment":"","conditions":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.reviewerId").isNotEmpty())
                .andExpect(jsonPath("$.violations.outcome").isNotEmpty())
                .andExpect(jsonPath("$.violations.comment").isNotEmpty());
    }

    private String createEvaluation(String policyNo, String question) throws Exception {
        return mvc.perform(post("/api/v1/underwriting/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNo":"%s","question":"%s"}
                                """.formatted(policyNo, question)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
