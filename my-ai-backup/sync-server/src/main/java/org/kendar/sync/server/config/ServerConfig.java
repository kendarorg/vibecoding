package org.kendar.sync.server.config;

import org.kendar.sync.lib.model.ServerSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Configuration for the server.
 */
@Configuration
public class ServerConfig {
    @Value("${server.settings.file:settings.json}")
    private String settingsFile;
    private ServerSettings serverSettings;

    /**
     * Creates a server settings bean.
     *
     * @return The server settings
     * @throws IOException If an I/O error occurs
     */
    @Bean
    public ServerSettings serverSettings() throws IOException {
        if (serverSettings == null) {
            serverSettings = ServerSettings.load(settingsFile);
        }
        return serverSettings;
    }

    /**
     * Reloads the server settings.
     *
     * @return The reloaded server settings
     * @throws IOException If an I/O error occurs
     */
    public ServerSettings reloadSettings() throws IOException {
        return ServerSettings.load(settingsFile);
    }

    /**
     * Gets the settings file path.
     *
     * @return The settings file path
     */
    public String getSettingsFile() {
        return settingsFile;
    }

    public void setServerSettings(ServerSettings serverSettings) {
        this.serverSettings = serverSettings;
    }
}