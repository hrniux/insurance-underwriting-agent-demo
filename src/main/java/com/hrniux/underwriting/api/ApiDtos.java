package com.hrniux.underwriting.api;

import java.util.Map;
import java.util.Set;

import com.hrniux.underwriting.agent.EvaluationRequest;
import com.hrniux.underwriting.rag.DocumentType;
import com.hrniux.underwriting.rag.KnowledgeDocument;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record EvaluationApiRequest(
            String sessionId,
            @NotBlank String policyNo,
            @NotBlank String question) {

        EvaluationRequest toDomain() {
            return new EvaluationRequest(sessionId, policyNo, question);
        }
    }

    public record KnowledgeDocumentRequest(
            @NotBlank String id,
            @NotBlank String title,
            @NotNull DocumentType type,
            @NotBlank String productCode,
            @NotBlank String content,
            Map<String, String> metadata) {

        KnowledgeDocument toDomain() {
            return new KnowledgeDocument(id, title, type, productCode, content,
                    metadata == null ? Map.of() : metadata);
        }
    }

    public record KnowledgeSearchRequest(
            @NotBlank String query,
            @Min(1) @Max(20) int topK,
            DocumentType documentType,
            String productCode) {

        public KnowledgeSearchRequest {
            topK = topK == 0 ? 4 : topK;
            productCode = productCode == null || productCode.isBlank() ? null : productCode;
        }
    }

    public record PromptVersionRequest(
            @NotBlank String body,
            @NotEmpty Set<String> requiredVariables) {
    }

    public record PromptPreviewRequest(@NotNull Map<String, Object> variables) {
    }

    public record PromptPreviewResponse(String rendered) {
    }

    public record ToolInvokeRequest(@NotBlank String policyNo) {
    }
}
