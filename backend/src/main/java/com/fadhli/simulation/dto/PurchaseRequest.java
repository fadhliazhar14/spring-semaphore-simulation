package com.fadhli.simulation.dto;

public class PurchaseRequest {
    private Long eventId;
    private String userId;

    public PurchaseRequest() {
    }

    public PurchaseRequest(Long eventId, String userId) {
        this.eventId = eventId;
        this.userId = userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
