package org.kendar.sync.server.config;

import jakarta.annotation.PostConstruct;
import org.kendar.sync.server.server.Server;
import org.springframework.stereotype.Component;

@Component
public class TcpServerRunner {
    private static boolean dryRun;
    private final ServerConfig serverConfig;

    public TcpServerRunner(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public static void setDryRun(boolean dryRun) {
        TcpServerRunner.dryRun = dryRun;
    }

    @PostConstruct
    public void init() {
        new Thread(() -> {
            var server = new Server(serverConfig, TcpServerRunner.dryRun);
            server.startTcpServer();
        }).start();
    }
}
