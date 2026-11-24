package com.example.shortener.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Locates repository-level files (docs, scripts) from the backend module working directory. */
public final class RepoFiles {
    private RepoFiles() {}

    public static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("docs/phases.json"))) {
            p = p.getParent();
        }
        if (p == null) throw new IllegalStateException("Repository root (docs/phases.json) not found");
        return p;
    }

    public static Path file(String relative) {
        return root().resolve(relative);
    }

    public static String read(String relative) {
        try {
            String s = Files.readString(file(relative), StandardCharsets.UTF_8);
            // Windows PowerShell 5.1 writes a UTF-8 BOM; strip it so JSON parsers accept the file.
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + relative, e);
        }
    }

    /** Appends one JSON line to docs/perf/results-latest.jsonl (consumed by scripts/gate-perf.ps1). */
    public static synchronized void recordPerf(String metric, Object budget, Object measured, boolean passed) {
        String line = String.format("{\"metric\":\"%s\",\"budget\":\"%s\",\"measured\":\"%s\",\"passed\":%s}%n",
                metric, budget, measured, passed);
        try {
            Path f = file("docs/perf/results-latest.jsonl");
            Files.createDirectories(f.getParent());
            Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
