package com.example.shortener.artifacts;

import com.example.shortener.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtifactService {
    private final ArtifactRepository repo;

    public ArtifactService(ArtifactRepository repo) {
        this.repo = repo;
    }

    /** Creates the next version for (run,node,type); the previous CURRENT version becomes SUPERSEDED. */
    @Transactional
    public Artifact create(long runId, String nodeId, String type, String content, String createdBy,
                           List<Long> parentIds, List<String> inputRefs) {
        List<Artifact> versions = repo.findByRunIdAndNodeIdAndArtifactTypeOrderByVersionAsc(runId, nodeId, type);
        int next = versions.isEmpty() ? 1 : versions.get(versions.size() - 1).getVersion() + 1;
        versions.stream().filter(a -> a.getStatus() == ArtifactStatus.CURRENT)
                .forEach(a -> a.changeStatus(ArtifactStatus.SUPERSEDED));
        String parents = parentIds == null ? "" : String.join(",", parentIds.stream().map(String::valueOf).toList());
        String refs = inputRefs == null ? "" : String.join(",", inputRefs);
        return repo.save(new Artifact(runId, nodeId, type, next, content, sha256(content), parents, refs,
                createdBy, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<Artifact> list(long runId) {
        return repo.findByRunIdOrderByIdAsc(runId);
    }

    @Transactional(readOnly = true)
    public Optional<Artifact> current(long runId, String nodeId, String type) {
        return repo.findByRunIdAndNodeIdAndArtifactTypeAndStatus(runId, nodeId, type, ArtifactStatus.CURRENT);
    }

    /** CURRENT -> STALE for the given ids (content untouched). Other statuses and unknown ids are left alone. */
    @Transactional
    public void markStale(Collection<Long> artifactIds) {
        repo.findAllById(artifactIds).stream().filter(a -> a.getStatus() == ArtifactStatus.CURRENT)
                .forEach(a -> a.changeStatus(ArtifactStatus.STALE));
    }

    /**
     * Rollback: makes an existing version CURRENT again and the previously current one SUPERSEDED.
     * Nothing is deleted or rewritten. Rule: an unknown version is 404; a STALE version is refused with 409
     * (its inputs changed, so it must be regenerated rather than restored); restoring the already CURRENT
     * version is a no-op. SUPERSEDED versions are restorable. {@code actor} identifies the caller; the caller
     * is responsible for writing the matching audit event.
     */
    @Transactional
    public Artifact restore(long runId, String nodeId, String type, int version, String actor) {
        List<Artifact> versions = repo.findByRunIdAndNodeIdAndArtifactTypeOrderByVersionAsc(runId, nodeId, type);
        Artifact target = versions.stream().filter(a -> a.getVersion() == version).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_VERSION_NOT_FOUND",
                        "No version " + version + " for " + nodeId + "/" + type));
        if (target.getStatus() == ArtifactStatus.STALE) {
            throw new ApiException(HttpStatus.CONFLICT, "ARTIFACT_STALE",
                    "Version " + version + " is stale and cannot be restored; regenerate it");
        }
        if (target.getStatus() == ArtifactStatus.CURRENT) return target;
        versions.stream().filter(a -> a.getStatus() == ArtifactStatus.CURRENT)
                .forEach(a -> a.changeStatus(ArtifactStatus.SUPERSEDED));
        target.changeStatus(ArtifactStatus.CURRENT);
        return target;
    }

    static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
