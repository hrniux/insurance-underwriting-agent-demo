package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ParagraphTextSplitterTest {

    private final ParagraphTextSplitter splitter = new ParagraphTextSplitter();

    @Test
    void splitsLongParagraphsWithinTheConfiguredLimit() {
        KnowledgeDocument document = document("第一段包含仓库暴雨风险和排水设施情况。第二句继续描述整改要求。\n\n第二段说明免赔额建议。");

        var chunks = splitter.split(document, 24, 5);

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.content().length()).isLessThanOrEqualTo(24);
            assertThat(chunk.documentId()).isEqualTo("DOC-001");
        });
        assertThat(chunks).extracting(DocumentChunk::index)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void carriesConfiguredOverlapIntoTheNextWindow() {
        KnowledgeDocument document = document("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

        var chunks = splitter.split(document, 20, 5);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo("ABCDEFGHIJKLMNOPQRST");
        assertThat(chunks.get(1).content()).startsWith("PQRST");
        assertThat(chunks.get(2).content()).startsWith("45678");
    }

    @Test
    void rejectsAnOverlapThatCannotAdvanceTheWindow() {
        assertThatThrownBy(() -> splitter.split(document("文本"), 10, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    private KnowledgeDocument document(String content) {
        return new KnowledgeDocument(
                "DOC-001", "仓储风险说明", DocumentType.RISK_GUIDE,
                "PROPERTY", content, Map.of("source", "test"));
    }
}
