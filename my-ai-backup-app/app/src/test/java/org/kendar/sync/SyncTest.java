package org.kendar.sync;

import static org.junit.Assert.assertTrue;

import com.kendar.testcontainers.images.JavaImage;

import org.junit.Test;
import org.kendar.sync.lib.utils.Sleeper;

import java.nio.file.Path;

public class SyncTest {
    @Test
    public void testJava() throws Exception {

        try (var javaImage = new JavaImage()) {
            javaImage
                    .withDir("/test")
                    .withFile(Path.of("..", "tc", "sync-server.jar").toString(), "/test/sync-server.jar")
                    .withCmd(Path.of("..", "tc", "run.sh").toString(), "/test/run.sh")
                    .withExposedPorts(
                            8090,   //Backup server
                            8089,   //Backup server ui
                            8180)   //Test content
                    .start();
            Sleeper.sleep(1000* 60,()->javaImage.isStarted()); //Wait for the server to start
            var testContentPort = javaImage.getMappedPort(8180);
            var backupServerPort = javaImage.getMappedPort(8090);
            var backupUiPort = javaImage.getMappedPort(8089);
            var logs = javaImage.getLogs();
            assertTrue(logs.contains("sync-server.jar"));
            assertTrue(logs.contains("run.sh"));


        }
    }
}
