package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class KnowledgeSeedLoaderTest {

    @Test
    void loadsFourFictionalDocumentsExactlyOnce() {
        EmbeddingService embedding = new HashEmbeddingService(128);
        KnowledgeService service = new KnowledgeService(
                new InMemoryKnowledgeRepository(),
                new TextDocumentParser(),
                new ParagraphTextSplitter(),
                new InMemoryVectorStore(embedding),
                180,
                30,
                4);
        KnowledgeSeedLoader loader = new KnowledgeSeedLoader(service, new DefaultResourceLoader());

        loader.seed();
        loader.seed();

        assertThat(service.list()).hasSize(4);
        assertThat(service.list()).extracting(KnowledgeDocument::type)
                .containsExactlyInAnyOrder(
                        DocumentType.PRODUCT_CLAUSE,
                        DocumentType.UNDERWRITING_RULE,
                        DocumentType.RISK_GUIDE,
                        DocumentType.HISTORICAL_CASE);
        assertThat(service.search("仓库暴雨排水整改", 4, null, "PROPERTY")).isNotEmpty();
    }
}
