package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore(new HashEmbeddingService(128));
        store.add(chunk("RAIN-0", "DOC-RAIN", DocumentType.UNDERWRITING_RULE,
                "PROPERTY", "暴雨 红色 预警 仓库 排水 人工复核"));
        store.add(chunk("FIRE-0", "DOC-FIRE", DocumentType.UNDERWRITING_RULE,
                "PROPERTY", "火灾 消防 喷淋 仓库 拒保"));
        store.add(chunk("CAR-0", "DOC-CAR", DocumentType.HISTORICAL_CASE,
                "AUTO", "车辆 碰撞 赔付 案例"));
    }

    @Test
    void ranksTheClosestKnowledgeFirst() {
        var hits = store.search("暴雨红色预警仓库排水", 2, null, null);

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().chunk().documentId()).isEqualTo("DOC-RAIN");
        assertThat(hits).isSortedAccordingTo(
                java.util.Comparator.comparingDouble(RetrievalHit::score).reversed());
    }

    @Test
    void filtersByDocumentTypeAndProductCode() {
        var hits = store.search("赔付案例", 5, DocumentType.HISTORICAL_CASE, "AUTO");

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.chunk().documentId()).isEqualTo("DOC-CAR");
            assertThat(hit.chunk().productCode()).isEqualTo("AUTO");
        });
    }

    @Test
    void exactTermsRecoverAClauseWhenTheEmbeddingHasNoSignal() {
        InMemoryVectorStore lexicalStore = new InMemoryVectorStore(ignored -> new double[8]);
        lexicalStore.add(chunk("RAIN-900-0", "CLAUSE-RAIN-900", DocumentType.PRODUCT_CLAUSE,
                "PROPERTY", "仓库累计免赔额按照附加条款执行"));

        var hits = lexicalStore.search("请定位 CLAUSE-RAIN-900", 4, null, "property");

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.chunk().documentId()).isEqualTo("CLAUSE-RAIN-900");
            assertThat(hit.mode()).isEqualTo(RetrievalMode.LEXICAL_ONLY);
            assertThat(hit.vectorScore()).isZero();
            assertThat(hit.lexicalScore()).isEqualTo(1.0);
            assertThat(hit.matchedTerms()).contains("clause", "rain", "900");
        });
    }

    @Test
    void exposesAStableHybridScoreBreakdown() {
        var hits = store.search("暴雨红色预警仓库排水", 2, null, "PROPERTY");

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst()).satisfies(hit -> {
            assertThat(hit.mode()).isEqualTo(RetrievalMode.HYBRID);
            assertThat(hit.score()).isBetween(0.0, 1.0);
            assertThat(hit.vectorScore()).isPositive();
            assertThat(hit.lexicalScore()).isPositive();
            assertThat(hit.matchedTerms()).contains("暴雨", "红色", "预警", "排水");
        });
    }

    @Test
    void atomicallyReplacesAndRemovesEveryChunkForADocument() {
        InMemoryVectorStore lifecycleStore = new InMemoryVectorStore(ignored -> new double[8]);
        lifecycleStore.add(chunk("VERSIONED-OLD-0", "DOC-VERSIONED", DocumentType.UNDERWRITING_RULE,
                "PROPERTY", "obsoletekeyword old clause"));
        lifecycleStore.add(chunk("OTHER-0", "DOC-OTHER", DocumentType.UNDERWRITING_RULE,
                "PROPERTY", "unrelatedkeyword other clause"));

        lifecycleStore.replaceDocument("DOC-VERSIONED", java.util.List.of(
                chunk("VERSIONED-NEW-0", "DOC-VERSIONED", DocumentType.UNDERWRITING_RULE,
                        "PROPERTY", "replacementkeyword new clause")));

        assertThat(lifecycleStore.search("obsoletekeyword", 4, null, "PROPERTY"))
                .noneMatch(hit -> hit.chunk().documentId().equals("DOC-VERSIONED"));
        assertThat(lifecycleStore.search("replacementkeyword", 4, null, "PROPERTY"))
                .singleElement()
                .satisfies(hit -> assertThat(hit.chunk().id()).isEqualTo("VERSIONED-NEW-0"));
        assertThat(lifecycleStore.size()).isEqualTo(2);

        lifecycleStore.removeDocument("DOC-VERSIONED");

        assertThat(lifecycleStore.search("replacementkeyword", 4, null, "PROPERTY")).isEmpty();
        assertThat(lifecycleStore.size()).isEqualTo(1);
    }

    @Test
    void rejectsAReplacementContainingChunksFromAnotherDocument() {
        assertThatThrownBy(() -> store.replaceDocument("DOC-RAIN", java.util.List.of(
                chunk("WRONG-0", "DOC-FIRE", DocumentType.UNDERWRITING_RULE,
                        "PROPERTY", "wrong document"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all chunks must belong");

        assertThat(store.search("暴雨红色预警", 4, null, "PROPERTY"))
                .anyMatch(hit -> hit.chunk().documentId().equals("DOC-RAIN"));
    }

    private DocumentChunk chunk(
            String id, String documentId, DocumentType type, String productCode, String content) {
        return new DocumentChunk(id, documentId, 0, "测试标题", type, productCode, content, Map.of());
    }
}
