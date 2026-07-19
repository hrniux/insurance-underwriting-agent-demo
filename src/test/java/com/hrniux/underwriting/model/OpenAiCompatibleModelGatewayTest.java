package com.hrniux.underwriting.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.rule.RuleEvaluation;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class OpenAiCompatibleModelGatewayTest {

    private static final String SECRET = "test-model-credential";

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsTheOpenAiChatSchemaAndBearerHeaderWithoutLeakingTheKey() throws Exception {
        server.enqueue(success("建议人工复核并补充整改材料"));
        OpenAiCompatibleModelGateway gateway = gateway(1, Duration.ZERO, Duration.ofSeconds(1));

        ModelResponse response = gateway.generate(request());
        RecordedRequest recorded = server.takeRequest();

        assertThat(recorded.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer " + SECRET);
        assertThat(recorded.getBody().readUtf8()).contains("demo-private-model", "请分析保单");
        assertThat(response.summary()).isEqualTo("建议人工复核并补充整改材料");
        assertThat(response.provider()).isEqualTo("openai-compatible");
        assertThat(gateway.toString()).doesNotContain(SECRET);
    }

    @Test
    void retriesTwoServerErrorsAndReportsTheThirdAttempt() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("first"));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("second"));
        server.enqueue(success("第三次调用成功"));

        ModelResponse response = gateway(3, Duration.ZERO, Duration.ofSeconds(1)).generate(request());

        assertThat(response.attempts()).isEqualTo(3);
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void preservesWarningsAndReturnsTargetedRecoveryActions() {
        server.enqueue(success("存在数据缺失，建议人工复核"));
        OpenAiCompatibleModelGateway gateway = gateway(1, Duration.ZERO, Duration.ofSeconds(1));
        ModelRequest request = new ModelRequest(
                "请分析保单",
                new RuleEvaluation(Decision.MANUAL_REVIEW, RiskLevel.LOW, 10, List.of()),
                List.of("示例证据"),
                List.of("灾害风险数据暂时不可用"));

        ModelResponse response = gateway.generate(request);

        assertThat(response.reasons()).contains("灾害风险数据暂时不可用");
        assertThat(response.recommendedActions()).containsExactly(
                "补充并核验缺失的外部灾害风险数据",
                "由人工核保人员确认完整资料后重新评估");
    }

    @Test
    void doesNotRetryAClientError() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad request"));

        assertThatThrownBy(() -> gateway(3, Duration.ZERO, Duration.ofSeconds(1)).generate(request()))
                .isInstanceOf(ModelUnavailableException.class)
                .satisfies(error -> assertThat(((ModelUnavailableException) error).attempts()).isOne())
                .hasMessageNotContaining(SECRET);
        assertThat(server.getRequestCount()).isOne();
    }

    @Test
    void mapsASocketReadTimeoutToTheDomainError() {
        server.enqueue(success("too late").setBodyDelay(300, TimeUnit.MILLISECONDS));

        assertThatThrownBy(() -> gateway(1, Duration.ZERO, Duration.ofMillis(50)).generate(request()))
                .isInstanceOf(ModelUnavailableException.class)
                .satisfies(error -> assertThat(((ModelUnavailableException) error).errorCode())
                        .isEqualTo("MODEL_UNAVAILABLE"));
    }

    private OpenAiCompatibleModelGateway gateway(int attempts, Duration backoff, Duration readTimeout) {
        return new OpenAiCompatibleModelGateway(
                server.url("/").toString(),
                SECRET,
                "demo-private-model",
                Duration.ofSeconds(1),
                readTimeout,
                attempts,
                backoff);
    }

    private ModelRequest request() {
        return new ModelRequest("请分析保单", new RuleEvaluation(
                Decision.MANUAL_REVIEW, RiskLevel.HIGH, 70, List.of()), List.of("示例证据"), List.of());
    }

    private MockResponse success(String content) {
        return new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}");
    }
}
