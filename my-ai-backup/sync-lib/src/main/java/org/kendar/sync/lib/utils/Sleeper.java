package org.kendar.sync.lib.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

/**
 * No thread lock wait
 */
@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class Sleeper {
    /**
     * Runs a synchronized-based wait mechanism instead of sleep
     *
     * @param timeoutMillis Timeout in ms
     */
    @SuppressWarnings("CatchMayIgnoreException")
    public static void sleep(long timeoutMillis) {

        try {
            if (timeoutMillis == 0) {
                Thread.onSpinWait();
                return;
            }
            Object obj = new Object();
            synchronized (obj) {
                obj.wait(timeoutMillis);
            }
        } catch (Exception ex) {

        }
    }

    @SuppressWarnings("CatchMayIgnoreException")
    public static void sleep(long timeoutMillis, BooleanSupplier booleanSupplier) {
        try {
            Object obj = new Object();

            var times = (int) timeoutMillis;
            var counter = 100;
            if (times <= 100) counter = 2;
            for (int i = 0; i < timeoutMillis; i += counter) {
                synchronized (obj) {
                    obj.wait(counter);
                }
                if (booleanSupplier.getAsBoolean()) {
                    return;
                }
            }

        } catch (Exception ex) {

        }
        throw new RuntimeException("Sleeper sleep timed out");
    }

    public static void sleepNoException(long timeoutMillis, BooleanSupplier booleanSupplier) {
        sleepNoException(timeoutMillis, booleanSupplier, false);
    }

    public static void sleepNoException(long timeoutMillis, BooleanSupplier booleanSupplier, boolean silent) {
        try {
            Object obj = new Object();
            var times = (int) timeoutMillis / 100;
            for (int i = 0; i < 100; i++) {
                synchronized (obj) {
                    obj.wait(times);
                }
                if (booleanSupplier.getAsBoolean()) {
                    return;
                }
            }

        } catch (Exception ex) {
            //NOOP
        }
        if (!silent) {
            log.debug("Sleeper sleep timed out with no answer");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Sleeper.class);
    /**
     * Give control to other threads
     */
    public static void yield() {
        Thread.onSpinWait();
        //Sleeper.sleep(1);
    }
}
