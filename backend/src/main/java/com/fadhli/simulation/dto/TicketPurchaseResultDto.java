package com.fadhli.simulation.dto;

public class TicketPurchaseResultDto {
    public enum Status {
        SUCCESS,
        FAILED_OUT_OF_STOCK,
        FAILED_TIMEOUT,
        ERROR
    }

    private Status status;
    private String message;
    private String userId;
    private Long eventId;
    private Integer remainingTickets;
    private long durationMs;

    public TicketPurchaseResultDto() {
    }

    public TicketPurchaseResultDto(Status status, String message, String userId, Long eventId, Integer remainingTickets, long durationMs) {
        this.status = status;
        this.message = message;
        this.userId = userId;
        this.eventId = eventId;
        this.remainingTickets = remainingTickets;
        this.durationMs = durationMs;
    }

    public static TicketPurchaseResultDto success(String userId, Long eventId, Integer remainingTickets, long durationMs) {
        return new TicketPurchaseResultDto(Status.SUCCESS, "Tiket berhasil dibeli!", userId, eventId, remainingTickets, durationMs);
    }

    public static TicketPurchaseResultDto outOfStock(String userId, Long eventId, long durationMs) {
        return new TicketPurchaseResultDto(Status.FAILED_OUT_OF_STOCK, "Tiket habis!", userId, eventId, 0, durationMs);
    }

    public static TicketPurchaseResultDto timeout(String userId, Long eventId, long durationMs) {
        return new TicketPurchaseResultDto(Status.FAILED_TIMEOUT, "Permit Semaphore timeout (server sibuk)!", userId, eventId, null, durationMs);
    }

    public static TicketPurchaseResultDto error(String userId, Long eventId, String message, long durationMs) {
        return new TicketPurchaseResultDto(Status.ERROR, message, userId, eventId, null, durationMs);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Integer getRemainingTickets() {
        return remainingTickets;
    }

    public void setRemainingTickets(Integer remainingTickets) {
        this.remainingTickets = remainingTickets;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
