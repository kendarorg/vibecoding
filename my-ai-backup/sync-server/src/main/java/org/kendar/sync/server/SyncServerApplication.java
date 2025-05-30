package org.kendar.sync.server;

import jakarta.servlet.Servlet;
import org.kendar.sync.server.config.ServerConfig;
import org.kendar.sync.server.config.SyncServerRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class for the sync server application.
 */
@SpringBootApplication
public class SyncServerApplication {

    private static final Logger log = LoggerFactory.getLogger(SyncServerApplication.class);
    @Autowired
    private ServerConfig serverConfig;
    @Autowired
    private Servlet servlet;
    @Autowired
    private SyncServerRunner runner;


    /**
     * Main method to start the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Parse command line arguments
        for (String arg : args) {
            if (arg.equals("--dry-run")) {
                SyncServerRunner.setDryRun(true);
                System.out.println("Running in dry-run mode. No actual file operations will be performed.");
            }
        }
        SpringApplication.run(SyncServerApplication.class);
    }


}
