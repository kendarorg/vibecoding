package org.kendar.sync.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class CaptureLogsAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
        System.out.println(eventObject.getFormattedMessage());
    }
}
