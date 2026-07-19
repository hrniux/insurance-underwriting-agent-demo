package com.hrniux.underwriting.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SearchTextAnalyzer {

    private SearchTextAnalyzer() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        StringBuilder hanRun = new StringBuilder();
        text.toLowerCase(Locale.ROOT).codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushWord(word, tokens);
                hanRun.appendCodePoint(codePoint);
            }
            else if (Character.isLetterOrDigit(codePoint)) {
                flushHan(hanRun, tokens);
                word.appendCodePoint(codePoint);
            }
            else {
                flushWord(word, tokens);
                flushHan(hanRun, tokens);
            }
        });
        flushWord(word, tokens);
        flushHan(hanRun, tokens);
        return List.copyOf(tokens);
    }

    private static void flushWord(StringBuilder word, List<String> tokens) {
        if (!word.isEmpty()) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private static void flushHan(StringBuilder hanRun, List<String> tokens) {
        if (hanRun.isEmpty()) {
            return;
        }
        int[] codePoints = hanRun.codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            tokens.add(new String(codePoints, index, 1));
        }
        for (int index = 0; index + 1 < codePoints.length; index++) {
            tokens.add(new String(codePoints, index, 2));
        }
        hanRun.setLength(0);
    }
}
