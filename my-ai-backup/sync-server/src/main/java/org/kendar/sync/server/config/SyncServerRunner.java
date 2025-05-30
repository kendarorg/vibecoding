package org.kendar.sync.server.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.kendar.sync.server.server.Server;
import org.springframework.stereotype.Component;

@Component
public class SyncServerRunner {
    private static boolean dryRun;
    private final ServerConfig serverConfig;
    private Server server;

    public SyncServerRunner(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public static void setDryRun(boolean dryRun) {
        SyncServerRunner.dryRun = dryRun;
    }

    @PostConstruct
    public void init() {
        new Thread(() -> {
            server = new Server(serverConfig, SyncServerRunner.dryRun);
            server.startTcpServer();
        }).start();
    }

    @PreDestroy
    public void destroy() {
        if (server != null) {
            server.stop();
        }
    }
}
