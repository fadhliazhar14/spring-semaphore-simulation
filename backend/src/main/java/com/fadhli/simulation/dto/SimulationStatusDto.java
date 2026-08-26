package com.fadhli.simulation.dto;

public class SimulationStatusDto {
    private int availableTickets;
    private int totalTickets;
    private int activePermits;
    private int totalPermits;
    private int queueLength;
    private int totalRequests;
    private int successRequests;
    private int failedOutOfStock;
    private int failedTimeout;
    private String message;
    private long timestamp;

    public SimulationStatusDto() {
    }

    public SimulationStatusDto(int availableTickets, int totalTickets, int activePermits, int totalPermits,
                               int queueLength, int totalRequests, int successRequests,
                               int failedOutOfStock, int failedTimeout, String message) {
        this.availableTickets = availableTickets;
        this.totalTickets = totalTickets;
        this.activePermits = activePermits;
        this.totalPermits = totalPermits;
        this.queueLength = queueLength;
        this.totalRequests = totalRequests;
        this.successRequests = successRequests;
        this.failedOutOfStock = failedOutOfStock;
        this.failedTimeout = failedTimeout;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public int getAvailableTickets() {
        return availableTickets;
    }

    public void setAvailableTickets(int availableTickets) {
        this.availableTickets = availableTickets;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(int totalTickets) {
        this.totalTickets = totalTickets;
    }

    public int getActivePermits() {
        return activePermits;
    }

    public void setActivePermits(int activePermits) {
        this.activePermits = activePermits;
    }

    public int getTotalPermits() {
        return totalPermits;
    }

    public void setTotalPermits(int totalPermits) {
        this.totalPermits = totalPermits;
    }

    public int getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getSuccessRequests() {
        return successRequests;
    }

    public void setSuccessRequests(int successRequests) {
        this.successRequests = successRequests;
    }

    public int getFailedOutOfStock() {
        return failedOutOfStock;
    }

    public void setFailedOutOfStock(int failedOutOfStock) {
        this.failedOutOfStock = failedOutOfStock;
    }

    public int getFailedTimeout() {
        return failedTimeout;
    }

    public void setFailedTimeout(int failedTimeout) {
        this.failedTimeout = failedTimeout;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
