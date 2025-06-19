package org.kendar.sync.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class CaptureLogsAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
        System.out.println(eventObject.getFormattedMessage());
    }
}
