package com.assassin.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of content moderation analysis
 */
public class ModerationResult {
    
    public enum Status {
        APPROVED,
        REJECTED,
        PENDING_MANUAL_REVIEW
    }
    
    public enum ContentType {
        TEXT,
        IMAGE,
        COMBINED
    }
    
    private Status status;
    private ContentType contentType;
    private double confidenceScore;
    private List<ModerationFlag> flags;
    private String reason;
    private Instant timestamp;
    private Map<String, Object> metadata;

    public ModerationResult() {
        this.timestamp = Instant.now();
    }

    public ModerationResult(Status status, ContentType contentType, List<ModerationFlag> flags) {
        this();
        this.status = status;
        this.contentType = contentType;
        this.flags = flags;
        this.confidenceScore = calculateConfidenceScore(flags);
    }

    /**
     * Calculate overall confidence score from flags
     */
    private double calculateConfidenceScore(List<ModerationFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            return 0.0;
        }
        return flags.stream()
                .mapToDouble(ModerationFlag::getConfidence)
                .max()
                .orElse(0.0);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public List<ModerationFlag> getFlags() {
        return flags;
    }

    public void setFlags(List<ModerationFlag> flags) {
        this.flags = flags;
        this.confidenceScore = calculateConfidenceScore(flags);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Check if this result indicates inappropriate content
     */
    public boolean isInappropriate() {
        return status == Status.REJECTED;
    }

    /**
     * Check if this result requires manual review
     */
    public boolean requiresManualReview() {
        return status == Status.PENDING_MANUAL_REVIEW;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationResult that = (ModerationResult) o;
        return Double.compare(that.confidenceScore, confidenceScore) == 0 &&
                status == that.status &&
                contentType == that.contentType &&
                Objects.equals(flags, that.flags) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, contentType, confidenceScore, flags, timestamp);
    }

    @Override
    public String toString() {
        return "ModerationResult{" +
                "status=" + status +
                ", contentType=" + contentType +
                ", confidenceScore=" + confidenceScore +
                ", flagsCount=" + (flags != null ? flags.size() : 0) +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
} 