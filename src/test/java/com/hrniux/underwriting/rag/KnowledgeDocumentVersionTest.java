package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class KnowledgeDocumentVersionTest {

    private static final Instant CREATED = Instant.parse("2026-07-19T10:00:00Z");
    private static final Instant PUBLISHED = Instant.parse("2026-07-19T10:01:00Z");
    private static final Instant RETIRED = Instant.parse("2026-07-19T10:02:00Z");

    @Test
    void enforcesDraftPublishedRetiredStateTransitions() {
        KnowledgeDocumentVersion draft = KnowledgeDocumentVersion.draft(document(), 1, CREATED);
        KnowledgeDocumentVersion published = draft.publish(PUBLISHED);
        KnowledgeDocumentVersion retired = published.retire(RETIRED);

        assertThat(draft.status()).isEqualTo(KnowledgeDocumentStatus.DRAFT);
        assertThat(published.status()).isEqualTo(KnowledgeDocumentStatus.PUBLISHED);
        assertThat(published.publishedAt()).isEqualTo(PUBLISHED);
        assertThat(retired.status()).isEqualTo(KnowledgeDocumentStatus.RETIRED);
        assertThat(retired.retiredAt()).isEqualTo(RETIRED);
    }

    @Test
    void rejectsInvalidTransitionsAndTimestamps() {
        KnowledgeDocumentVersion draft = KnowledgeDocumentVersion.draft(document(), 1, CREATED);

        assertThatThrownBy(() -> draft.retire(RETIRED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected PUBLISHED");
        assertThatThrownBy(() -> draft.publish(CREATED.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishedAt");
    }

    private KnowledgeDocument document() {
        return new KnowledgeDocument(
                "DOC-LIFECYCLE",
                "生命周期测试",
                DocumentType.UNDERWRITING_RULE,
                "PROPERTY",
                "测试正文",
                Map.of());
    }
}
