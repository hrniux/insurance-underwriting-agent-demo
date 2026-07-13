package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;

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

    private DocumentChunk chunk(
            String id, String documentId, DocumentType type, String productCode, String content) {
        return new DocumentChunk(id, documentId, 0, "测试标题", type, productCode, content, Map.of());
    }
}
