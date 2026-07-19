package com.hrniux.underwriting.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hrniux.underwriting.agent.EvaluationRequest;
import com.hrniux.underwriting.rag.DocumentType;
import com.hrniux.underwriting.rag.KnowledgeDocument;
import com.hrniux.underwriting.rag.RetrievalEvaluationCase;
import com.hrniux.underwriting.review.HumanReviewCommand;
import com.hrniux.underwriting.review.HumanReviewOutcome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    public record HumanReviewApiRequest(
            @NotBlank @Size(max = 64) String reviewerId,
            @NotNull HumanReviewOutcome outcome,
            @NotBlank @Size(max = 1_000) String comment,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> conditions) {

        HumanReviewCommand toDomain() {
            return new HumanReviewCommand(reviewerId, outcome, comment, conditions);
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

    public record KnowledgeVersionRequest(
            @NotBlank String title,
            @NotNull DocumentType type,
            @NotBlank String productCode,
            @NotBlank String content,
            Map<String, String> metadata) {

        KnowledgeDocument toDomain(String documentId) {
            return new KnowledgeDocument(documentId, title, type, productCode, content,
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

    public record KnowledgeEvaluationRequest(
            @NotEmpty @Size(max = 100) List<@Valid KnowledgeEvaluationCaseRequest> cases,
            @Min(1) @Max(20) int topK,
            @DecimalMin("0.0") @DecimalMax("1.0") double minimumRecallAtK,
            @DecimalMin("0.0") @DecimalMax("1.0") double minimumMeanReciprocalRank) {

        public KnowledgeEvaluationRequest {
            topK = topK == 0 ? 4 : topK;
        }
    }

    public record KnowledgeEvaluationCaseRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 1_000) String query,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 128) String> expectedDocumentIds,
            DocumentType documentType,
            @Size(max = 64) String productCode) {

        RetrievalEvaluationCase toDomain() {
            return new RetrievalEvaluationCase(name, query, expectedDocumentIds, documentType, productCode);
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
