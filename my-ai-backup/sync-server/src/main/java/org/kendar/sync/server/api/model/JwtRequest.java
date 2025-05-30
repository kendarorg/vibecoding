package org.kendar.sync.server.api.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Request model for JWT authentication.
 */
public class JwtRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 5926468583005150707L;

    private String username;
    private String password;

    // Default constructor for JSON parsing
    public JwtRequest() {
    }

    /**
     * Creates a new JWT request.
     *
     * @param username The username
     * @param password The password
     */
    public JwtRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}