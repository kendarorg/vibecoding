package org.kendar.sync.server;

import org.kendar.sync.server.config.SyncServerRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main class for the sync server application.
 */
@SpringBootApplication
public class SyncServerApplication {
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
