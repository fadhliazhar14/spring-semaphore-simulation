package com.fadhli.simulation.dto;

public class InitSimulationRequest {
    private String eventName;
    private int totalTickets;
    private int semaphorePermits;
    private Boolean useDelay = true;

    public InitSimulationRequest() {
    }

    public InitSimulationRequest(String eventName, int totalTickets, int semaphorePermits) {
        this(eventName, totalTickets, semaphorePermits, true);
    }

    public InitSimulationRequest(String eventName, int totalTickets, int semaphorePermits, Boolean useDelay) {
        this.eventName = eventName;
        this.totalTickets = totalTickets;
        this.semaphorePermits = semaphorePermits;
        this.useDelay = useDelay != null ? useDelay : true;
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

    public Boolean getUseDelay() {
        return useDelay;
    }

    public void setUseDelay(Boolean useDelay) {
        this.useDelay = useDelay;
    }
}
