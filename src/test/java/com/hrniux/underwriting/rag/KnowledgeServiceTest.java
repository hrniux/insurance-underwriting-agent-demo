package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.shared.error.ConflictException;

class KnowledgeServiceTest {

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        EmbeddingService embedding = new HashEmbeddingService(128);
        service = new KnowledgeService(
                new InMemoryKnowledgeRepository(),
                new TextDocumentParser(),
                new ParagraphTextSplitter(),
                new InMemoryVectorStore(embedding),
                120,
                20,
                4);
    }

    @Test
    void ingestsParsesSplitsAndRetrievesAKnowledgeDocument() {
        KnowledgeDocument document = rainstormRule();

        KnowledgeIngestionResult result = service.ingest(document);
        var hits = service.search(
                "暴雨红色预警下仓库应该如何核保",
                4,
                DocumentType.UNDERWRITING_RULE,
                "PROPERTY");

        assertThat(result.documentId()).isEqualTo("RULE-RAIN-001");
        assertThat(result.chunkCount()).isPositive();
        assertThat(service.list()).containsExactly(document);
        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().chunk().documentId()).isEqualTo("RULE-RAIN-001");
        assertThat(hits.getFirst().chunk().metadata()).containsEntry("source", "demo-rulebook");
    }

    @Test
    void rejectsDuplicateDocumentIds() {
        service.ingest(rainstormRule());

        assertThatThrownBy(() -> service.ingest(rainstormRule()))
                .isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(((ConflictException) error).errorCode())
                        .isEqualTo("KNOWLEDGE_DOCUMENT_EXISTS"));
    }

    private KnowledgeDocument rainstormRule() {
        return new KnowledgeDocument(
                "RULE-RAIN-001",
                "企财险暴雨风险核保规则",
                DocumentType.UNDERWRITING_RULE,
                "PROPERTY",
                "# 暴雨核保规则\n\n暴雨红色预警区域内的仓库标的，应核验排水设施和防汛整改证明；整改未完成时必须人工复核。",
                Map.of("source", "demo-rulebook"));
    }
}
