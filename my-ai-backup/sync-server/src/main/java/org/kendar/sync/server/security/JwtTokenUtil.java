package org.kendar.sync.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.kendar.sync.lib.model.ServerSettings;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for JWT token operations.
 */
@Component
public class JwtTokenUtil {
    // Token validity time in milliseconds (30 minutes)
    private static final long JWT_TOKEN_VALIDITY = 30 * 60 * 1000;

    // Secret key for signing the token
    private final Key key;

    /**
     * Creates a new JWT token utility.
     */
    public JwtTokenUtil() {
        // Generate a secure key for HS256 algorithm
        this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    }

    /**
     * Retrieves username from JWT token.
     *
     * @param token The JWT token
     * @return The username
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Retrieves expiration date from JWT token.
     *
     * @param token The JWT token
     * @return The expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * Retrieves a claim from the token.
     *
     * @param token          The JWT token
     * @param claimsResolver The claims resolver function
     * @param <T>            The type of the claim
     * @return The claim
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Retrieves all claims from the token.
     *
     * @param token The JWT token
     * @return The claims
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks if the token has expired.
     *
     * @param token The JWT token
     * @return True if the token has expired, false otherwise
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * Generates a token for a user.
     *
     * @param userDetails The user details
     * @param userId      The user ID
     * @param isAdmin     Whether the user is an admin
     * @return The JWT token
     */
    public String generateToken(UserDetails userDetails, String userId, boolean isAdmin) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("isAdmin", isAdmin);
        return doGenerateToken(claims, userDetails.getUsername());
    }

    /**
     * Generates a token for a user.
     *
     * @param user The user
     * @return The JWT token
     */
    public String generateToken(ServerSettings.User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("isAdmin", user.isAdmin());
        return doGenerateToken(claims, user.getUsername());
    }

    /**
     * Generates a token.
     *
     * @param claims  The claims
     * @param subject The subject (username)
     * @return The JWT token
     */
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(key)
                .compact();
    }

    /**
     * Validates a token.
     *
     * @param token       The JWT token
     * @param userDetails The user details
     * @return True if the token is valid, false otherwise
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * Gets the user ID from the token.
     *
     * @param token The JWT token
     * @return The user ID
     */
    public String getUserIdFromToken(String token) {
        return getClaimFromToken(token, claims -> (String) claims.get("userId"));
    }

    /**
     * Checks if the user is an admin.
     *
     * @param token The JWT token
     * @return True if the user is an admin, false otherwise
     */
    public Boolean isAdmin(String token) {
        return getClaimFromToken(token, claims -> (Boolean) claims.get("isAdmin"));
    }
}
