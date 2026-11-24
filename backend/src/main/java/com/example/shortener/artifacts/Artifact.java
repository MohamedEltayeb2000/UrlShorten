package com.example.shortener.artifacts;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable versioned stage output. Only {@code status} may change after creation. */
@Entity
@Table(name = "artifact")
public class Artifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "node_id", nullable = false, length = 64, updatable = false)
    private String nodeId;

    @Column(name = "artifact_type", nullable = false, length = 48, updatable = false)
    private String artifactType;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, length = 100000, updatable = false)
    private String content;

    @Column(nullable = false, length = 64, updatable = false)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArtifactStatus status;

    /** Comma separated ids of the artifacts this one was derived from. */
    @Column(name = "parent_artifact_ids", nullable = false, length = 500, updatable = false)
    private String parentArtifactIds;

    /** Comma separated input references (e.g. user input or settings keys). */
    @Column(name = "input_refs", nullable = false, length = 1000, updatable = false)
    private String inputRefs;

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Artifact() {}

    public Artifact(Long runId, String nodeId, String artifactType, int version, String content, String checksum,
                    String parentArtifactIds, String inputRefs, String createdBy, Instant createdAt) {
        this.runId = runId;
        this.nodeId = nodeId;
        this.artifactType = artifactType;
        this.version = version;
        this.content = content;
        this.checksum = checksum;
        this.status = ArtifactStatus.CURRENT;
        this.parentArtifactIds = parentArtifactIds;
        this.inputRefs = inputRefs;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getNodeId() { return nodeId; }
    public String getArtifactType() { return artifactType; }
    public int getVersion() { return version; }
    public String getContent() { return content; }
    public String getChecksum() { return checksum; }
    public ArtifactStatus getStatus() { return status; }
    public String getParentArtifactIds() { return parentArtifactIds; }
    public String getInputRefs() { return inputRefs; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    void changeStatus(ArtifactStatus newStatus) { this.status = newStatus; }
}
