package org.kendar.sync.model;

import org.kendar.sync.util.ScheduleUtil;

import java.util.Calendar;
import java.util.UUID;

public class Job {
    private UUID id;
    private String name;
    private String serverAddress;
    private int serverPort;
    private String login;
    private String password;
    private String localSource;
    private String targetDestination;
    private String scheduleTime;
    private String lastExecution;
    private int lastTransferred;
    private int uiServerPort;

    public Calendar retrieveNextScheduleTime(){
        return ScheduleUtil.getNextScheduledTime(scheduleTime);
    }

    public boolean retrieveIsOnWifiOnly() {
        return ScheduleUtil.isOnWifiOnly(scheduleTime);
    }

    public boolean retrieveIsOnChargeOnly() {
        return ScheduleUtil.isOnChargeOnly(scheduleTime);
    }

    public boolean retrieveIsOnStartup() {
        return ScheduleUtil.isOnStartup(scheduleTime);
    }

    public Job() {
        // Default constructor
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLocalSource() {
        return localSource;
    }

    public void setLocalSource(String localSource) {
        this.localSource = localSource;
    }

    public String getTargetDestination() {
        return targetDestination;
    }

    public void setTargetDestination(String targetDestination) {
        this.targetDestination = targetDestination;
    }

    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
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

    public int getUiServerPort() {
        return uiServerPort;
    }

    public void setUiServerPort(int uiServerPort) {
        this.uiServerPort = uiServerPort;
    }
}