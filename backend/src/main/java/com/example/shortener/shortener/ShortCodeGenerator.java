package com.example.shortener.shortener;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final int length;

    public ShortCodeGenerator(@Value("${shortener.code-length:7}") int length) {
        this.length = Math.max(4, Math.min(length, 32));
    }

    public String next() {
        char[] c = new char[length];
        for (int i = 0; i < length; i++) c[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(c);
    }
}
