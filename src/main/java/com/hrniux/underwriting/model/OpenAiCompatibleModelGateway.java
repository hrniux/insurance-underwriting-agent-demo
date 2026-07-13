package com.hrniux.underwriting.model;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class OpenAiCompatibleModelGateway implements ModelGateway {

    private static final String ERROR_CODE = "MODEL_UNAVAILABLE";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final RestClient restClient;

    public OpenAiCompatibleModelGateway(
            String baseUrl,
            String apiKey,
            String model,
            Duration connectTimeout,
            Duration readTimeout,
            int maxAttempts,
            Duration retryBackoff) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoff = retryBackoff == null ? Duration.ZERO : retryBackoff;

        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String content = execute(request);
                return new ModelResponse(
                        content,
                        request.ruleEvaluation().hits().stream().map(hit -> hit.reason()).toList(),
                        List.of("结合确定性规则与证据执行人工核保复核"),
                        "openai-compatible",
                        model,
                        attempt,
                        false);
            }
            catch (RestClientResponseException error) {
                if (isRetryable(error.getStatusCode().value()) && attempt < maxAttempts) {
                    waitBeforeRetry();
                    continue;
                }
                throw unavailable("Model endpoint returned HTTP " + error.getStatusCode().value(), attempt, error);
            }
            catch (ResourceAccessException error) {
                if (attempt < maxAttempts) {
                    waitBeforeRetry();
                    continue;
                }
                throw unavailable("Model endpoint was unavailable or timed out", attempt, error);
            }
            catch (ModelUnavailableException error) {
                throw error;
            }
            catch (RuntimeException error) {
                throw unavailable("Model response could not be processed", attempt, error);
            }
        }
        throw unavailable("Model endpoint was unavailable", maxAttempts, null);
    }

    @SuppressWarnings("unchecked")
    private String execute(ModelRequest request) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是财险智能核保助手，规则结论不可被模型降级。"),
                        Map.of("role", "user", "content", request.renderedPrompt())));

        Map<String, Object> body = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(payload)
                .retrieve()
                .body(Map.class);
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("empty model response");
            }
            return content;
        }
        catch (NullPointerException | ClassCastException | IndexOutOfBoundsException error) {
            throw unavailable("Model response schema was invalid", 1, error);
        }
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry() {
        if (retryBackoff.isZero() || retryBackoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryBackoff);
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable("Model retry was interrupted", 1, error);
        }
    }

    private ModelUnavailableException unavailable(String message, int attempts, Throwable cause) {
        return new ModelUnavailableException(ERROR_CODE, message, attempts, cause);
    }

    @Override
    public String toString() {
        return "OpenAiCompatibleModelGateway[baseUrl=%s, model=%s, apiKey=REDACTED]".formatted(baseUrl, model);
    }
}
