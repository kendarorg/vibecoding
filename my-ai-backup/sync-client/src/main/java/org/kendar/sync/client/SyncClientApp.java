package org.kendar.sync.client;

import org.kendar.sync.lib.protocol.BackupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Main class for the sync client application.
 */
public class SyncClientApp {
    private static final Logger log = LoggerFactory.getLogger(SyncClientApp.class);

    /**
     * Main method to start the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Parse command line arguments
        CommandLineArgs commandLineArgs = parseCommandLineArgs(args);

        if (commandLineArgs.isHelp()) {
            printHelp();
            return;
        }
        var hostname= getHostname();
        var syncClient = new SyncClient();
        commandLineArgs.setHostName(hostname);

        syncClient.doSync(commandLineArgs);
    }

    private static String getHostname() {
        var hostnames = new ArrayList<String>();
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec("hostname");
            var in = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                var trimmed = inputLine.trim().toUpperCase();
                if(isValid(trimmed))continue;
                hostnames.add(inputLine);
            }
            in.close();
        }catch (Exception ex){

        }
        try{
            var hostname =  InetAddress.getLocalHost().getHostName()
                    .trim().toUpperCase();
            if(!isValid(hostname) && !hostnames.contains(hostname)){
                hostnames.add(hostname);
            }
        }catch (Exception ex){

        }
        if(hostnames.isEmpty()) {
            hostnames.add("localhost");
        }
        return hostnames.get(0);
    }

    private static boolean isValid(String trimmed) {
        return trimmed.isEmpty() ||
                trimmed.equalsIgnoreCase("localhost") ||
                trimmed.equalsIgnoreCase("127.0.0.1");
    }


    /**
     * Parses command line arguments.
     *
     * @param args The command line arguments
     * @return The parsed arguments
     */
    private static CommandLineArgs parseCommandLineArgs(String[] args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--help":
                case "-h":
                    commandLineArgs.setHelp(true);
                    break;
                case "--source":
                case "-s":
                    if (i + 1 < args.length) {
                        commandLineArgs.setSourceFolder(args[++i]);
                    }
                    break;
                case "--conn":
                case "-c":
                    if (i + 1 < args.length) {
                        commandLineArgs.setMaxConnections(Integer.parseInt(args[++i]));
                    }
                    break;
                case "--size":
                case "-z":
                    if (i + 1 < args.length) {
                        commandLineArgs.setMaxSize(Integer.parseInt(args[++i]));
                    }
                    break;
                case "--target":
                case "-t":
                    if (i + 1 < args.length) {
                        commandLineArgs.setTargetFolder(args[++i]);
                    }
                    break;
                case "--backup":
                case "-b":
                    commandLineArgs.setBackup(true);
                    break;
                case "--restore":
                case "-r":
                    commandLineArgs.setBackup(false);
                    break;
                case "--server":
                    if (i + 1 < args.length) {
                        commandLineArgs.setServerAddress(args[++i]);
                    }
                    break;
                case "--port":
                case "-p":
                    if (i + 1 < args.length) {
                        try {
                            commandLineArgs.setServerPort(Integer.parseInt(args[++i]));
                        } catch (NumberFormatException e) {
                            log.error("[CLIENT] Invalid port number: {}", args[i]);
                        }
                    }
                    break;
                case "--username":
                case "-u":
                    if (i + 1 < args.length) {
                        commandLineArgs.setUsername(args[++i]);
                    }
                    break;
                case "--password":
                case "-pw":
                    if (i + 1 < args.length) {
                        commandLineArgs.setPassword(args[++i]);
                    }
                    break;
                case "--dry-run":
                case "-d":
                    commandLineArgs.setDryRun(true);
                    break;
                case "--type":
                    if (i + 1 < args.length) {
                        String typeArg = args[++i].toUpperCase();
                        try {
                            commandLineArgs.setBackupType(BackupType.valueOf(typeArg));
                        } catch (IllegalArgumentException e) {
                            log.error("[CLIENT] Invalid backup type: {}", typeArg);
                            log.error("[CLIENT] Valid types are: PRESERVE, MIRROR, DATE_SEPARATED");
                        }
                    }
                    break;
            }
        }

        return commandLineArgs;
    }

    /**
     * Prints help information.
     */
    private static void printHelp() {
        System.out.println("Usage: java -jar sync-client.jar [options]");
        System.out.println("Options:");
        System.out.println("  --help, -h                  Show this help message");
        System.out.println("  --source, -s <folder>       Source folder");
        System.out.println("  --target, -t <folder>       Target folder (virtual folder on server)");
        System.out.println("  --backup, -b                Perform backup (default)");
        System.out.println("  --restore, -r               Perform restore");
        System.out.println("  --conn, -c                  Max connections");
        System.out.println("  --size, -z                  Max packet size in bytes");
        System.out.println("  --server <address>          Server address");
        System.out.println("  --port, -p <port>           Server port (default: 8090)");
        System.out.println("  --username, -u <username>   Username for authentication");
        System.out.println("  --password, -pw <password>  Password for authentication");
        System.out.println("  --dry-run, -d               Perform a dry run (no actual file operations)");
        System.out.println("  --type <type>               Backup type: PRESERVE, MIRROR, DATE_SEPARATED (default: PRESERVE)");
    }

}
