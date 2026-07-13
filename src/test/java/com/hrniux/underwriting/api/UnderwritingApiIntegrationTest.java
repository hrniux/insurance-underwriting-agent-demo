package com.hrniux.underwriting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class UnderwritingApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsAndReadsASessionThenEvaluatesAndReadsTheResult() throws Exception {
        String sessionJson = mvc.perform(post("/api/v1/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(sessionJson, "$.id");

        mvc.perform(get("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));

        String evaluationJson = mvc.perform(post("/api/v1/underwriting/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","policyNo":"P-1001","question":"这张仓库财产险能否承保？"}
                                """.formatted(sessionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.evidence").isNotEmpty())
                .andExpect(jsonPath("$.toolTraces.length()").value(6))
                .andExpect(jsonPath("$.stepTraces.length()").value(7))
                .andReturn().getResponse().getContentAsString();
        String evaluationId = JsonPath.read(evaluationJson, "$.id");

        mvc.perform(get("/api/v1/underwriting/evaluations/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evaluationId));
    }

    @Test
    void returnsAConsistentValidationProblem() throws Exception {
        mvc.perform(post("/api/v1/underwriting/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyNo\":\"\",\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void propagatesTheCallerTraceIdOnANotFoundProblem() throws Exception {
        mvc.perform(get("/api/v1/sessions/SES-MISSING").header("X-Trace-Id", "trace-interview-001"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", "trace-interview-001"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-interview-001"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
