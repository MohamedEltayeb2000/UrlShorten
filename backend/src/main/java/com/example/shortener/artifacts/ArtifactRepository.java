package com.example.shortener.artifacts;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {
    List<Artifact> findByRunIdOrderByIdAsc(Long runId);

    List<Artifact> findByRunIdAndNodeIdAndArtifactTypeOrderByVersionAsc(Long runId, String nodeId, String type);

    Optional<Artifact> findByRunIdAndNodeIdAndArtifactTypeAndStatus(Long runId, String nodeId, String type,
                                                                    ArtifactStatus status);
}
