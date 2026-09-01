package com.fadhli.simulation.dto;

import java.util.List;

public class SimulationStatusDto {
    private int availableTickets;
    private int totalTickets;
    private int activePermits;
    private int totalPermits;
    private int totalRequests;
    private int successRequests;
    private int failedOutOfStock;
    private int failedRejected;
    private int failedPayment;
    private int abandoned;
    private int gaveUp;
    private String message;
    private long timestamp;
    /** Keadaan tiap slot, kosong bila belum ada simulasi. */
    private List<SlotStateDto> slots = List.of();
    /** Instance yang menyusun snapshot ini, bukan instance yang memegang slot. */
    private String reportedBy;
    /** Panjang tenggat sesi, supaya papan tahu skala bar lease-nya. */
    private long sessionLeaseMs;
    /** Seluruh instance yang tercatat, hidup maupun yang baru saja padam. */
    private List<InstanceStateDto> instances = List.of();

    public SimulationStatusDto() {
    }

    public SimulationStatusDto(int availableTickets, int totalTickets, int activePermits, int totalPermits,
                               int totalRequests, int successRequests,
                               int failedOutOfStock, int failedRejected,
                               int failedPayment, int abandoned, int gaveUp, String message) {
        this.availableTickets = availableTickets;
        this.totalTickets = totalTickets;
        this.activePermits = activePermits;
        this.totalPermits = totalPermits;
        this.totalRequests = totalRequests;
        this.successRequests = successRequests;
        this.failedOutOfStock = failedOutOfStock;
        this.failedRejected = failedRejected;
        this.failedPayment = failedPayment;
        this.abandoned = abandoned;
        this.gaveUp = gaveUp;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public List<SlotStateDto> getSlots() {
        return slots;
    }

    public void setSlots(List<SlotStateDto> slots) {
        this.slots = (slots == null) ? List.of() : slots;
    }

    public List<InstanceStateDto> getInstances() {
        return instances;
    }

    public void setInstances(List<InstanceStateDto> instances) {
        this.instances = (instances == null) ? List.of() : instances;
    }

    public long getSessionLeaseMs() {
        return sessionLeaseMs;
    }

    public void setSessionLeaseMs(long sessionLeaseMs) {
        this.sessionLeaseMs = sessionLeaseMs;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
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

    public int getFailedRejected() {
        return failedRejected;
    }

    public void setFailedRejected(int failedRejected) {
        this.failedRejected = failedRejected;
    }

    public int getFailedPayment() {
        return failedPayment;
    }

    public void setFailedPayment(int failedPayment) {
        this.failedPayment = failedPayment;
    }

    public int getAbandoned() {
        return abandoned;
    }

    public void setAbandoned(int abandoned) {
        this.abandoned = abandoned;
    }

    public int getGaveUp() {
        return gaveUp;
    }

    public void setGaveUp(int gaveUp) {
        this.gaveUp = gaveUp;
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
