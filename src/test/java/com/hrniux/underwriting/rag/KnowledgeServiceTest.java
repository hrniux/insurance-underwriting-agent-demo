package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.shared.error.ConflictException;

class KnowledgeServiceTest {

    private KnowledgeService service;
    private InMemoryVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        EmbeddingService embedding = ignored -> new double[16];
        vectorStore = new InMemoryVectorStore(embedding);
        service = new KnowledgeService(
                new InMemoryKnowledgeRepository(),
                new InMemoryKnowledgeVersionRepository(),
                new TextDocumentParser(),
                new ParagraphTextSplitter(),
                vectorStore,
                120,
                20,
                4,
                Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC));
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

    @Test
    void publishesReplacesAndRetiresVersionedKnowledgeWithoutExposingDrafts() {
        KnowledgeDocument first = versionedRule("obsoletekeyword old wording");

        KnowledgeDocumentVersion draftOne = service.createDraft(first);

        assertThat(draftOne.status()).isEqualTo(KnowledgeDocumentStatus.DRAFT);
        assertThat(service.list()).isEmpty();
        assertThat(service.search("obsoletekeyword", 4, null, "PROPERTY")).isEmpty();

        KnowledgePublicationResult firstPublication = service.publish(first.id(), 1);
        KnowledgeDocumentVersion draftTwo = service.createVersion(
                first.id(), versionedRule("replacementkeyword corrected wording"));

        assertThat(firstPublication.chunkCount()).isPositive();
        assertThat(service.search("obsoletekeyword", 4, null, "PROPERTY"))
                .singleElement()
                .satisfies(hit -> assertThat(hit.chunk().metadata())
                        .containsEntry("knowledgeVersion", "1"));
        assertThat(draftTwo.version()).isEqualTo(2);
        assertThat(service.search("replacementkeyword", 4, null, "PROPERTY")).isEmpty();

        service.publish(first.id(), 2);

        assertThat(service.search("obsoletekeyword", 4, null, "PROPERTY")).isEmpty();
        assertThat(service.search("replacementkeyword", 4, null, "PROPERTY"))
                .singleElement()
                .satisfies(hit -> assertThat(hit.chunk().metadata())
                        .containsEntry("knowledgeVersion", "2"));
        assertThat(service.versions(first.id()))
                .extracting(KnowledgeDocumentVersion::status)
                .containsExactly(KnowledgeDocumentStatus.RETIRED, KnowledgeDocumentStatus.PUBLISHED);

        KnowledgeDocumentVersion retired = service.retire(first.id(), 2);

        assertThat(retired.status()).isEqualTo(KnowledgeDocumentStatus.RETIRED);
        assertThat(service.list()).isEmpty();
        assertThat(service.search("replacementkeyword", 4, null, "PROPERTY")).isEmpty();
        assertThat(vectorStore.size()).isZero();
    }

    @Test
    void rejectsParallelDraftsAndInvalidLifecycleTransitions() {
        KnowledgeDocument document = versionedRule("initialkeyword");
        service.createDraft(document);

        assertThatThrownBy(() -> service.createVersion(document.id(), versionedRule("secondkeyword")))
                .isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(((ConflictException) error).errorCode())
                        .isEqualTo("KNOWLEDGE_DRAFT_EXISTS"));

        service.publish(document.id(), 1);

        assertThatThrownBy(() -> service.publish(document.id(), 1))
                .isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(((ConflictException) error).errorCode())
                        .isEqualTo("INVALID_KNOWLEDGE_TRANSITION"));
        KnowledgeDocumentVersion second = service.createVersion(document.id(), versionedRule("secondkeyword"));
        assertThatThrownBy(() -> service.retire(document.id(), second.version()))
                .isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(((ConflictException) error).errorCode())
                        .isEqualTo("INVALID_KNOWLEDGE_TRANSITION"));
    }

    @Test
    void keepsThePublishedVersionAndIndexWhenBuildingANewIndexFails() {
        EmbeddingService failingEmbedding = text -> {
            if (text.contains("badembedding replacement")) {
                throw new IllegalStateException("simulated embedding failure");
            }
            return new double[16];
        };
        KnowledgeService guardedService = new KnowledgeService(
                new InMemoryKnowledgeRepository(),
                new InMemoryKnowledgeVersionRepository(),
                new TextDocumentParser(),
                new ParagraphTextSplitter(),
                new InMemoryVectorStore(failingEmbedding),
                120,
                20,
                4,
                Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC));
        guardedService.ingest(versionedRule("stablekeyword current published wording"));
        guardedService.createVersion(
                "RULE-VERSIONED-001", versionedRule("badembedding replacement wording"));

        assertThatThrownBy(() -> guardedService.publish("RULE-VERSIONED-001", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated embedding failure");

        assertThat(guardedService.versions("RULE-VERSIONED-001"))
                .extracting(KnowledgeDocumentVersion::status)
                .containsExactly(KnowledgeDocumentStatus.PUBLISHED, KnowledgeDocumentStatus.DRAFT);
        assertThat(guardedService.search("stablekeyword", 4, null, "PROPERTY"))
                .singleElement()
                .satisfies(hit -> assertThat(hit.chunk().metadata())
                        .containsEntry("knowledgeVersion", "1"));
        assertThat(guardedService.search("badembedding", 4, null, "PROPERTY")).isEmpty();
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

    private KnowledgeDocument versionedRule(String content) {
        return new KnowledgeDocument(
                "RULE-VERSIONED-001",
                "版本化核保规则",
                DocumentType.UNDERWRITING_RULE,
                "PROPERTY",
                content,
                Map.of("source", "lifecycle-test"));
    }
}
