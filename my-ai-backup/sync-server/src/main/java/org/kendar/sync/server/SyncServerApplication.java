package org.kendar.sync.server;

import jakarta.servlet.Servlet;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.config.ServerConfig;
import org.kendar.sync.server.server.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main class for the sync server application.
 */
@SpringBootApplication
public class SyncServerApplication implements CommandLineRunner {

    @Autowired
    private ServerConfig serverConfig;
    @Autowired
    private Servlet servlet;

    private boolean dryRun = false;


    /**
     * Main method to start the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {

        SpringApplication.run(SyncServerApplication.class, args);
    }

    /**
     * Command line runner that starts the TCP server.
     *
     * @param args Command line arguments
     */
    @Override
    public void run(String... args) {
        // Parse command line arguments
        for (String arg : args) {
            if (arg.equals("--dry-run")) {
                dryRun = true;
                System.out.println("Running in dry-run mode. No actual file operations will be performed.");
            }
        }

        new Thread(() -> {
            var server = new Server(serverConfig,dryRun );
            server.startTcpServer();
        }).start();
    }
}
