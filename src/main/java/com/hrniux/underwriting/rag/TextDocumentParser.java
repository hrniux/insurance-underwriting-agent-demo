package com.hrniux.underwriting.rag;

import org.springframework.stereotype.Component;

@Component
public class TextDocumentParser {

    public String parse(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        return source.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
