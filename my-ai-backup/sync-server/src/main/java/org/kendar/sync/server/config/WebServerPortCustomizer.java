package org.kendar.sync.server.config;

import org.kendar.sync.lib.model.ServerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class WebServerPortCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    private static final Logger log = LoggerFactory.getLogger(WebServerPortCustomizer.class);
    private final ServerSettings serverSettings;

    public WebServerPortCustomizer(ServerSettings serverSettings) {
        this.serverSettings = serverSettings;
    }
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        log.info("Starting Web server on port {}", serverSettings.getWebPort());
        factory.setPort(serverSettings.getWebPort());
    }
}
