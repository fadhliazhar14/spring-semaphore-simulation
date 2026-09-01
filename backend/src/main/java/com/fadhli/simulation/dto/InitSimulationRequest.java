package com.fadhli.simulation.dto;

public class InitSimulationRequest {
    private String eventName;
    private int totalTickets;
    private int semaphorePermits;
    /** Jeda pembeli antar langkah. Ini pula knob untuk memperlambat simulasi. */
    private int thinkTimeMs = 300;
    /** Peluang pembayaran berhasil, dalam persen. */
    private int paymentSuccessPercent = 90;

    public InitSimulationRequest() {
    }

    public InitSimulationRequest(String eventName, int totalTickets, int semaphorePermits) {
        this.eventName = eventName;
        this.totalTickets = totalTickets;
        this.semaphorePermits = semaphorePermits;
        this.thinkTimeMs = 300;
    }

    public InitSimulationRequest(String eventName, int totalTickets, int semaphorePermits, int thinkTimeMs) {
        this.eventName = eventName;
        this.totalTickets = totalTickets;
        this.semaphorePermits = semaphorePermits;
        this.thinkTimeMs = thinkTimeMs;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(int totalTickets) {
        this.totalTickets = totalTickets;
    }

    public int getSemaphorePermits() {
        return semaphorePermits;
    }

    public void setSemaphorePermits(int semaphorePermits) {
        this.semaphorePermits = semaphorePermits;
    }

    public int getThinkTimeMs() {
        return thinkTimeMs;
    }

    public void setThinkTimeMs(int thinkTimeMs) {
        this.thinkTimeMs = thinkTimeMs;
    }

    public int getPaymentSuccessPercent() {
        return paymentSuccessPercent;
    }

    public void setPaymentSuccessPercent(int paymentSuccessPercent) {
        this.paymentSuccessPercent = paymentSuccessPercent;
    }
}
