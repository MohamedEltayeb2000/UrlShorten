package com.example.shortener.agents.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the seeded brownfield fixture (plain text, read-only) and derives impacted files, data flow and
 * compatibility risks from its content. Deterministic: files are processed in sorted order.
 */
public class BrownfieldAnalyzer {

    /** Result of scanning the fixture. All lists are sorted/stable. */
    public record Analysis(List<String> impactedFiles, List<String> dataFlow, List<String> compatibilityRisks) {}

    private static final Pattern WRITE_PATH = Pattern.compile("(?i)create|insert|post");

    private final Path fixtureRoot;

    public BrownfieldAnalyzer() {
        this(null);
    }

    public BrownfieldAnalyzer(Path fixtureRoot) {
        this.fixtureRoot = fixtureRoot;
    }

    /** Walks up from the working directory until fixtures/brownfield is found. */
    static Path locateFixture() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("fixtures").resolve("brownfield");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("fixtures/brownfield not found above " + Path.of("").toAbsolutePath());
    }

    public Analysis analyze() {
        Path root = fixtureRoot != null ? fixtureRoot : locateFixture();
        Path src = root.resolve("src");
        List<String> impacted = new ArrayList<>();
        List<String> flow = new ArrayList<>();
        String serviceText = "";
        String schemaText = "";
        try (Stream<Path> files = Files.walk(src)) {
            List<Path> sorted = files.filter(Files::isRegularFile).sorted().toList();
            for (Path file : sorted) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (!WRITE_PATH.matcher(text).find()) {
                    continue;
                }
                impacted.add(rel);
                flow.add(rel + " -> " + firstMatchingLine(text));
                if (rel.endsWith("LinkService.java")) serviceText = text;
                if (rel.endsWith("schema.sql")) schemaText = text;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        flow.sort(null);

        List<String> risks = new ArrayList<>();
        if (serviceText.contains("repository.insert") && !serviceText.toLowerCase().contains("idempot")) {
            risks.add("LinkService.createLink inserts a new row on every call, so a retried POST /links creates a duplicate short link");
        }
        if (!schemaText.toLowerCase().contains("idempot")) {
            risks.add("schema.sql has no idempotency record table or unique key; adding one needs a migration that tolerates existing duplicate rows");
        }
        risks.add("Existing clients do not send an Idempotency-Key header, so the header must stay optional");
        risks.add("Changing the response for a replayed request must keep the original status code and body shape");
        return new Analysis(List.copyOf(impacted), List.copyOf(flow), List.copyOf(risks));
    }

    private static String firstMatchingLine(String text) {
        for (String line : text.split("\\R")) {
            if (WRITE_PATH.matcher(line).find()) {
                return line.trim();
            }
        }
        return "";
    }
}
