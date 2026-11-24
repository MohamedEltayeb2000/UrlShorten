package com.example.shortener.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Versioned evaluation criteria loaded from eval/criteria.json (002-FR-010, 002-FR-013). */
public record EvaluationCriteria(String version, double passThreshold, List<Criterion> criteria) {

    public record Criterion(String id, String description) {}

    public static EvaluationCriteria load(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);
            JsonNode root = new ObjectMapper().readTree(text);
            List<Criterion> list = new ArrayList<>();
            for (JsonNode c : root.get("criteria")) {
                list.add(new Criterion(c.get("id").asText(), c.path("description").asText("")));
            }
            return new EvaluationCriteria(root.get("criteriaVersion").asText(), root.get("passThreshold").asDouble(), list);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load criteria from " + path, e);
        }
    }

    public List<String> ids() {
        return criteria.stream().map(Criterion::id).toList();
    }
}
