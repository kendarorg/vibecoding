package org.kendar.sync.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.protocol.BackupType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SyncClientAppTest {

    private Path testRoot;
    private File sourceDir;
    private File targetDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() throws IOException {
        // Create a unique test directory inside target/tests
        String uniqueId = UUID.randomUUID().toString();
        testRoot = Path.of("target", "tests", uniqueId);
        Files.createDirectories(testRoot);
        
        // Create source and target directories
        sourceDir = new File(testRoot.toFile(), "source");
        targetDir = new File(testRoot.toFile(), "target");
        sourceDir.mkdir();
        targetDir.mkdir();
        
        // Create a test file in the source directory
        File testFile = new File(sourceDir, "testFile.txt");
        Files.writeString(testFile.toPath(), "Test content");
        
        // Redirect System.out for testing help output
        System.setOut(new PrintStream(outContent));
    }
    
    @Test
    void testParseCommandLineArgs() throws Exception {
        // Get the private parseCommandLineArgs method using reflection
        Method parseCommandLineArgs = SyncClientApp.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        parseCommandLineArgs.setAccessible(true);
        
        // Test backup command
        String[] backupArgs = {
            "--backup",
            "--source", sourceDir.getAbsolutePath(),
            "--target", "documents",
            "--server", "localhost",
            "--port", "8080",
            "--username", "testuser",
            "--password", "password123",
            "--type", "MIRROR"
        };
        
        Object result = parseCommandLineArgs.invoke(null, (Object) backupArgs);
        
        // Get the CommandLineArgs class
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.CommandLineArgs");
        
        // Get field values using reflection
        Field sourceFolder = commandLineArgsClass.getDeclaredField("sourceFolder");
        sourceFolder.setAccessible(true);
        Field targetFolder = commandLineArgsClass.getDeclaredField("targetFolder");
        targetFolder.setAccessible(true);
        Field backup = commandLineArgsClass.getDeclaredField("backup");
        backup.setAccessible(true);
        Field serverAddress = commandLineArgsClass.getDeclaredField("serverAddress");
        serverAddress.setAccessible(true);
        Field serverPort = commandLineArgsClass.getDeclaredField("serverPort");
        serverPort.setAccessible(true);
        Field username = commandLineArgsClass.getDeclaredField("username");
        username.setAccessible(true);
        Field password = commandLineArgsClass.getDeclaredField("password");
        password.setAccessible(true);
        Field backupType = commandLineArgsClass.getDeclaredField("backupType");
        backupType.setAccessible(true);
        
        // Verify the parsed arguments
        assertEquals(sourceDir.getAbsolutePath(), sourceFolder.get(result));
        assertEquals("documents", targetFolder.get(result));
        assertTrue((Boolean) backup.get(result));
        assertEquals("localhost", serverAddress.get(result));
        assertEquals(8080, serverPort.get(result));
        assertEquals("testuser", username.get(result));
        assertEquals("password123", password.get(result));
        assertEquals(BackupType.MIRROR, backupType.get(result));
    }
    
    @Test
    void testParseCommandLineArgsRestore() throws Exception {
        // Get the private parseCommandLineArgs method using reflection
        Method parseCommandLineArgs = SyncClientApp.class.getDeclaredMethod("parseCommandLineArgs", String[].class);
        parseCommandLineArgs.setAccessible(true);
        
        // Test restore command
        String[] restoreArgs = {
            "--restore",
            "--source", "documents",
            "--target", targetDir.getAbsolutePath(),
            "--server", "localhost",
            "--port", "8080",
            "--username", "testuser",
            "--password", "password123",
            "--type", "PRESERVE"
        };
        
        Object result = parseCommandLineArgs.invoke(null, (Object) restoreArgs);
        
        // Get the CommandLineArgs class
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.CommandLineArgs");
        
        // Get field values using reflection
        Field sourceFolder = commandLineArgsClass.getDeclaredField("sourceFolder");
        sourceFolder.setAccessible(true);
        Field targetFolder = commandLineArgsClass.getDeclaredField("targetFolder");
        targetFolder.setAccessible(true);
        Field backup = commandLineArgsClass.getDeclaredField("backup");
        backup.setAccessible(true);
        Field backupType = commandLineArgsClass.getDeclaredField("backupType");
        backupType.setAccessible(true);
        
        // Verify the parsed arguments
        assertEquals("documents", sourceFolder.get(result));
        assertEquals(targetDir.getAbsolutePath(), targetFolder.get(result));
        assertFalse((Boolean) backup.get(result));
        assertEquals(BackupType.PRESERVE, backupType.get(result));
    }
    
    @Test
    void testValidateArgs() throws Exception {
        // Get the private validateArgs method using reflection
        Method validateArgs = SyncClientApp.class.getDeclaredMethod("validateArgs", 
            Class.forName("org.kendar.sync.client.CommandLineArgs"));
        validateArgs.setAccessible(true);
        
        // Get the CommandLineArgs class
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.CommandLineArgs");
        
        // Create a valid args object using reflection
        Object args = commandLineArgsClass.getDeclaredConstructor().newInstance();
        
        // Set field values using reflection
        Field sourceFolder = commandLineArgsClass.getDeclaredField("sourceFolder");
        sourceFolder.setAccessible(true);
        sourceFolder.set(args, sourceDir.getAbsolutePath());
        
        Field targetFolder = commandLineArgsClass.getDeclaredField("targetFolder");
        targetFolder.setAccessible(true);
        targetFolder.set(args, "documents");
        
        Field backup = commandLineArgsClass.getDeclaredField("backup");
        backup.setAccessible(true);
        backup.set(args, true);
        
        Field serverAddress = commandLineArgsClass.getDeclaredField("serverAddress");
        serverAddress.setAccessible(true);
        serverAddress.set(args, "localhost");
        
        Field serverPort = commandLineArgsClass.getDeclaredField("serverPort");
        serverPort.setAccessible(true);
        serverPort.set(args, 8080);
        
        Field username = commandLineArgsClass.getDeclaredField("username");
        username.setAccessible(true);
        username.set(args, "testuser");
        
        Field password = commandLineArgsClass.getDeclaredField("password");
        password.setAccessible(true);
        password.set(args, "password123");
        
        // Validate the args
        Boolean result = (Boolean) validateArgs.invoke(null, args);
        
        // Verify the result
        assertTrue(result);
        
        // Test with invalid source folder
        sourceFolder.set(args, "/nonexistent/folder");
        result = (Boolean) validateArgs.invoke(null, args);
        assertFalse(result);
        
        // Reset source folder
        sourceFolder.set(args, sourceDir.getAbsolutePath());
        
        // Test with missing server address
        serverAddress.set(args, null);
        result = (Boolean) validateArgs.invoke(null, args);
        assertFalse(result);
    }
    
    @Test
    void testPrintHelp() throws Exception {
        // Get the private printHelp method using reflection
        Method printHelp = SyncClientApp.class.getDeclaredMethod("printHelp");
        printHelp.setAccessible(true);
        
        // Call the printHelp method
        printHelp.invoke(null);
        
        // Verify the output
        String output = outContent.toString();
        assertTrue(output.contains("Usage:"));
        assertTrue(output.contains("--backup"));
        assertTrue(output.contains("--restore"));
        assertTrue(output.contains("--source"));
        assertTrue(output.contains("--target"));
        assertTrue(output.contains("--server"));
        assertTrue(output.contains("--port"));
        assertTrue(output.contains("--username"));
        assertTrue(output.contains("--password"));
        assertTrue(output.contains("--type"));
    }
    
    @Test
    void testCommandLineArgsGettersAndSetters() throws Exception {
        // Get the CommandLineArgs class
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.CommandLineArgs");
        
        // Create a CommandLineArgs object
        Object args = commandLineArgsClass.getDeclaredConstructor().newInstance();
        
        // Test getters and setters
        Method setSourceFolder = commandLineArgsClass.getDeclaredMethod("setSourceFolder", String.class);
        Method getSourceFolder = commandLineArgsClass.getDeclaredMethod("getSourceFolder");
        setSourceFolder.invoke(args, "source");
        assertEquals("source", getSourceFolder.invoke(args));
        
        Method setTargetFolder = commandLineArgsClass.getDeclaredMethod("setTargetFolder", String.class);
        Method getTargetFolder = commandLineArgsClass.getDeclaredMethod("getTargetFolder");
        setTargetFolder.invoke(args, "target");
        assertEquals("target", getTargetFolder.invoke(args));
        
        Method setBackup = commandLineArgsClass.getDeclaredMethod("setBackup", boolean.class);
        Method isBackup = commandLineArgsClass.getDeclaredMethod("isBackup");
        setBackup.invoke(args, true);
        assertTrue((Boolean) isBackup.invoke(args));
        
        Method setServerAddress = commandLineArgsClass.getDeclaredMethod("setServerAddress", String.class);
        Method getServerAddress = commandLineArgsClass.getDeclaredMethod("getServerAddress");
        setServerAddress.invoke(args, "localhost");
        assertEquals("localhost", getServerAddress.invoke(args));
        
        Method setServerPort = commandLineArgsClass.getDeclaredMethod("setServerPort", int.class);
        Method getServerPort = commandLineArgsClass.getDeclaredMethod("getServerPort");
        setServerPort.invoke(args, 8080);
        assertEquals(8080, getServerPort.invoke(args));
        
        Method setUsername = commandLineArgsClass.getDeclaredMethod("setUsername", String.class);
        Method getUsername = commandLineArgsClass.getDeclaredMethod("getUsername");
        setUsername.invoke(args, "user");
        assertEquals("user", getUsername.invoke(args));
        
        Method setPassword = commandLineArgsClass.getDeclaredMethod("setPassword", String.class);
        Method getPassword = commandLineArgsClass.getDeclaredMethod("getPassword");
        setPassword.invoke(args, "pass");
        assertEquals("pass", getPassword.invoke(args));
        
        Method setDryRun = commandLineArgsClass.getDeclaredMethod("setDryRun", boolean.class);
        Method isDryRun = commandLineArgsClass.getDeclaredMethod("isDryRun");
        setDryRun.invoke(args, true);
        assertTrue((Boolean) isDryRun.invoke(args));
        
        Method setBackupType = commandLineArgsClass.getDeclaredMethod("setBackupType", BackupType.class);
        Method getBackupType = commandLineArgsClass.getDeclaredMethod("getBackupType");
        setBackupType.invoke(args, BackupType.MIRROR);
        assertEquals(BackupType.MIRROR, getBackupType.invoke(args));
        
        Method setHelp = commandLineArgsClass.getDeclaredMethod("setHelp", boolean.class);
        Method isHelp = commandLineArgsClass.getDeclaredMethod("isHelp");
        setHelp.invoke(args, true);
        assertTrue((Boolean) isHelp.invoke(args));
    }
}