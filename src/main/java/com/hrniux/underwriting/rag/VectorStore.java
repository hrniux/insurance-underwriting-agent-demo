package com.hrniux.underwriting.rag;

import java.util.List;

public interface VectorStore {

    void add(DocumentChunk chunk);

    List<RetrievalHit> search(String query, int topK, DocumentType type, String productCode);

    int size();
}
