package com.example.shortener.shortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shortener.support.Requirement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-023")
class ShortCodeGeneratorTest {

    @Test
    void codesHaveConfiguredLengthAndCharset() {
        ShortCodeGenerator g = new ShortCodeGenerator(8);
        for (int i = 0; i < 200; i++) {
            String c = g.next();
            assertEquals(8, c.length());
            assertTrue(c.matches("[0-9A-Za-z]+"));
        }
    }

    @Test
    void codesAreEffectivelyUnique() {
        ShortCodeGenerator g = new ShortCodeGenerator(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) seen.add(g.next());
        assertEquals(5000, seen.size());
    }
}
