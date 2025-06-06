package com.kendar.sync.model;

import java.util.UUID;

public class Job {
    private UUID id;
    private String name;
    private String lastExecution;
    private int lastTransferred;

    public Job() {
        // Default constructor
    }

    public Job(UUID id, String name, String lastExecution, int lastTransferred) {
        this.id = id;
        this.name = name;
        this.lastExecution = lastExecution;
        this.lastTransferred = lastTransferred;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(String lastExecution) {
        this.lastExecution = lastExecution;
    }

    public int getLastTransferred() {
        return lastTransferred;
    }

    public void setLastTransferred(int lastTransferred) {
        this.lastTransferred = lastTransferred;
    }
}