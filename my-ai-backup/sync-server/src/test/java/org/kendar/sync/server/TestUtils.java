package org.kendar.sync.server;

import org.junit.jupiter.api.TestInfo;

import java.nio.file.Path;

public class TestUtils {
    public static String getTestFolder(TestInfo testInfo) {
        if (testInfo != null && testInfo.getTestClass().isPresent() &&
                testInfo.getTestMethod().isPresent()) {
            var className = testInfo.getTestClass().get().getSimpleName();
            var method = testInfo.getTestMethod().get().getName();


            if (testInfo.getDisplayName().startsWith("[")) {
                var dsp = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9_\\-,.]", "_");
                return Path.of(className, method, dsp).toString();
            } else {
                return Path.of(className, method).toString();
            }
        }
        return "default";
    }
}
