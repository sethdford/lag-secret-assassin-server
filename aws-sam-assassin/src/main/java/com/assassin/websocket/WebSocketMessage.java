package com.assassin.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    private String type;
    private Object payload;
    private Long timestamp;
    private String requestId;

    public WebSocketMessage() {}

    public WebSocketMessage(String type, Object payload, Long timestamp, String requestId) {
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
        this.requestId = requestId;
    }

    @JsonProperty("type")
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @JsonProperty("payload")
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    @JsonProperty("timestamp")
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    @JsonProperty("requestId")
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "type='" + type + '\'' +
                ", payload=" + payload +
                ", timestamp=" + timestamp +
                ", requestId='" + requestId + '\'' +
                '}';
    }
} 