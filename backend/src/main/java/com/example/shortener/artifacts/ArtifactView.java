package com.example.shortener.artifacts;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record ArtifactView(Long id, String nodeId, String type, int version, ArtifactStatus status, String checksum,
                           List<Long> parentIds, List<String> inputRefs, String createdBy, Instant createdAt,
                           String content) {
    static ArtifactView of(Artifact a) {
        return new ArtifactView(a.getId(), a.getNodeId(), a.getArtifactType(), a.getVersion(), a.getStatus(),
                a.getChecksum(), split(a.getParentArtifactIds()).stream().map(Long::valueOf).toList(),
                split(a.getInputRefs()), a.getCreatedBy(), a.getCreatedAt(), a.getContent());
    }

    private static List<String> split(String s) {
        return s.isEmpty() ? List.of() : Arrays.asList(s.split(","));
    }
}
