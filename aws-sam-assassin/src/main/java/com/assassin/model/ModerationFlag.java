package com.assassin.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a moderation flag from AWS services (Rekognition or Comprehend)
 */
public class ModerationFlag {
    private String flagType;      // e.g., "Explicit Nudity", "Violence", "Hate Speech"
    private double confidence;    // Confidence score from AWS service
    private String source;        // "Rekognition" or "Comprehend"
    private Map<String, Object> details; // Additional metadata from AWS service

    public ModerationFlag() {}

    public ModerationFlag(String flagType, double confidence, String source, Map<String, Object> details) {
        this.flagType = flagType;
        this.confidence = confidence;
        this.source = source;
        this.details = details;
    }

    public String getFlagType() {
        return flagType;
    }

    public void setFlagType(String flagType) {
        this.flagType = flagType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationFlag that = (ModerationFlag) o;
        return Double.compare(that.confidence, confidence) == 0 &&
                Objects.equals(flagType, that.flagType) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flagType, confidence, source);
    }

    @Override
    public String toString() {
        return "ModerationFlag{" +
                "flagType='" + flagType + '\'' +
                ", confidence=" + confidence +
                ", source='" + source + '\'' +
                ", details=" + details +
                '}';
    }
} 