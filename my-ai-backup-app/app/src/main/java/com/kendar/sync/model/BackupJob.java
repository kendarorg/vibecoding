package com.kendar.sync.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Data model class representing a backup job
 */
public class BackupJob implements Serializable {
    private long id;
    private String name;
    private String localDirectory;
    private String remoteAddress;
    private int remotePort;
    private String remoteTarget;
    private String login;
    private String password;
    private Schedule schedule;
    private boolean wifiOnly;
    private boolean chargingOnly;
    private Date lastRunTime;
    private long lastRunDuration; // in milliseconds
    private Date nextScheduledRun;

    public BackupJob() {
        // Default values
        this.wifiOnly = true;
        this.chargingOnly = false;
        this.schedule = new Schedule();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalDirectory() {
        return localDirectory;
    }

    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = localDirectory;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public String getRemoteTarget() {
        return remoteTarget;
    }

    public void setRemoteTarget(String remoteTarget) {
        this.remoteTarget = remoteTarget;
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

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public boolean isWifiOnly() {
        return wifiOnly;
    }

    public void setWifiOnly(boolean wifiOnly) {
        this.wifiOnly = wifiOnly;
    }

    public boolean isChargingOnly() {
        return chargingOnly;
    }

    public void setChargingOnly(boolean chargingOnly) {
        this.chargingOnly = chargingOnly;
    }

    public Date getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(Date lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    public long getLastRunDuration() {
        return lastRunDuration;
    }

    public void setLastRunDuration(long lastRunDuration) {
        this.lastRunDuration = lastRunDuration;
    }

    public Date getNextScheduledRun() {
        return nextScheduledRun;
    }

    public void setNextScheduledRun(Date nextScheduledRun) {
        this.nextScheduledRun = nextScheduledRun;
    }
}
