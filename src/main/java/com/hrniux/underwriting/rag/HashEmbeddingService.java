package com.hrniux.underwriting.rag;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class HashEmbeddingService implements EmbeddingService {

    private final int dimensions;

    public HashEmbeddingService() {
        this(256);
    }

    public HashEmbeddingService(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }

        for (String token : tokenize(text)) {
            byte[] hash = sha256(token);
            int dimension = Math.floorMod(ByteBuffer.wrap(hash, 0, 4).getInt(), dimensions);
            double sign = (hash[4] & 1) == 0 ? 1.0 : -1.0;
            vector[dimension] += sign;
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        text.toLowerCase(Locale.ROOT).codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushWord(word, tokens);
                tokens.add(new String(Character.toChars(codePoint)));
            } else if (Character.isLetterOrDigit(codePoint)) {
                word.appendCodePoint(codePoint);
            } else {
                flushWord(word, tokens);
            }
        });
        flushWord(word, tokens);
        return tokens;
    }

    private void flushWord(StringBuilder word, List<String> tokens) {
        if (!word.isEmpty()) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void normalize(double[] vector) {
        double squaredNorm = 0.0;
        for (double value : vector) {
            squaredNorm += value * value;
        }
        if (squaredNorm == 0.0) {
            return;
        }
        double norm = Math.sqrt(squaredNorm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
    }
}
