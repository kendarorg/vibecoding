package org.kendar.sync.server.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors client sessions for timeout/expiration and disconnects expired sessions.
 */
public class SessionMonitor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SessionMonitor.class);
    private final Map<UUID, ClientSession> sessions;
    private final ScheduledExecutorService scheduler;
    private final long monitorIntervalSeconds;

    /**
     * Creates a new session monitor.
     *
     * @param sessions               The map of active sessions to monitor
     * @param monitorIntervalSeconds The interval in seconds between checks for expired sessions
     */
    public SessionMonitor(Map<UUID, ClientSession> sessions, long monitorIntervalSeconds) {
        this.sessions = sessions;
        this.monitorIntervalSeconds = monitorIntervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts the session monitor.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkSessions,
                monitorIntervalSeconds,
                monitorIntervalSeconds,
                TimeUnit.SECONDS);
        log.debug("Session monitor started, checking sessions every {} seconds", monitorIntervalSeconds);
    }

    /**
     * Checks all sessions for expiration and disconnects expired sessions.
     */
    private void checkSessions() {
        try {
            log.trace("Checking {} sessions for expiration", sessions.size());

            // Create a copy of the session IDs to avoid concurrent modification
            UUID[] sessionIds = sessions.keySet().toArray(new UUID[0]);

            for (UUID sessionId : sessionIds) {
                ClientSession session = sessions.get(sessionId);
                if (session != null && session.isExpired()) {
                    log.info("Session {} has expired, closing connections", sessionId);
                    session.closeConnections();
                    sessions.remove(sessionId);
                }
            }
        } catch (Exception e) {
            log.error("Error checking sessions: {}", e.getMessage(), e);
        }
    }

    /**
     * Stops the session monitor.
     */
    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.debug("Session monitor stopped");
    }
}
