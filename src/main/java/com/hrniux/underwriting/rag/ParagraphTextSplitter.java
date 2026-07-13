package com.hrniux.underwriting.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ParagraphTextSplitter {

    public List<DocumentChunk> split(KnowledgeDocument document, int maxCharacters, int overlapCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("maxCharacters must be positive");
        }
        if (overlapCharacters < 0 || overlapCharacters >= maxCharacters) {
            throw new IllegalArgumentException("overlapCharacters must be between zero and maxCharacters");
        }

        List<String> parts = new ArrayList<>();
        for (String paragraph : document.content().replace("\r\n", "\n").split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (!normalized.isEmpty()) {
                splitParagraph(normalized, maxCharacters, overlapCharacters, parts);
            }
        }

        List<DocumentChunk> chunks = new ArrayList<>(parts.size());
        for (int index = 0; index < parts.size(); index++) {
            chunks.add(new DocumentChunk(
                    document.id() + "-CHUNK-" + index,
                    document.id(),
                    index,
                    document.title(),
                    document.type(),
                    document.productCode(),
                    parts.get(index),
                    document.metadata()));
        }
        return List.copyOf(chunks);
    }

    private void splitParagraph(String paragraph, int maxCharacters, int overlapCharacters, List<String> target) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxCharacters, paragraph.length());
            target.add(paragraph.substring(start, end));
            if (end == paragraph.length()) {
                return;
            }
            start = end - overlapCharacters;
        }
    }
}
