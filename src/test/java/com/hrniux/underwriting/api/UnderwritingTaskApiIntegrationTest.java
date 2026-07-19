package com.hrniux.underwriting.api;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class UnderwritingTaskApiIntegrationTest {

    private static final String TASKS = "/api/v1/underwriting/tasks";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void acceptsPollsListsAndIdempotentlyReplaysAnAsyncEvaluation() throws Exception {
        String key = "async-api-" + UUID.randomUUID();
        String body = """
                {"policyNo":"P-2001","question":"异步分析这张低风险办公楼保单。"}
                """;

        String accepted = mvc.perform(post(TASKS)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith(TASKS + "/TASK-")))
                .andExpect(jsonPath("$.status", anyOf(is("PENDING"), is("RUNNING"), is("SUCCEEDED"))))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(accepted, "$.id");

        String completed = awaitTerminal(taskId);
        String evaluationId = JsonPath.read(completed, "$.evaluationId");
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(completed, "$.status"))
                .isEqualTo("SUCCEEDED");

        mvc.perform(get("/api/v1/underwriting/evaluations/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNo").value("P-2001"));

        mvc.perform(post(TASKS)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.evaluationId").value(evaluationId));

        mvc.perform(post(TASKS)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNo":"P-1001","question":"同键异参必须冲突。"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));

        mvc.perform(get(TASKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(taskId)).exists());

        mvc.perform(get("/actuator/metrics/underwriting.task.submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'outcome')]").exists());
        mvc.perform(get("/actuator/metrics/underwriting.task.duration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'outcome')]").exists());
        mvc.perform(get("/actuator/metrics/underwriting.task.transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'status')]").exists());
    }

    @Test
    void exposesSafeAsyncFailureAndConsistentNotFoundProblem() throws Exception {
        String accepted = mvc.perform(post(TASKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyNo":"P-MISSING","question":"不存在的保单应异步失败。"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(accepted, "$.id");
        String failed = awaitTerminal(taskId);

        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(failed, "$.status"))
                .isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(failed, "$.failure.errorCode"))
                .isNotBlank();
        Object evaluationId = JsonPath.read(failed, "$.evaluationId");
        org.assertj.core.api.Assertions.assertThat(evaluationId).isNull();

        mvc.perform(get(TASKS + "/TASK-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("UNDERWRITING_TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(TASKS + "/TASK-MISSING"));
    }

    private String awaitTerminal(String taskId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        String body = "";
        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mvc.perform(get(TASKS + "/{id}", taskId))
                    .andExpect(status().isOk())
                    .andReturn();
            body = result.getResponse().getContentAsString();
            String taskStatus = JsonPath.read(body, "$.status");
            if (taskStatus.equals("SUCCEEDED") || taskStatus.equals("FAILED")) {
                return body;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Task did not reach a terminal state: " + body);
    }
}
